package com.mzx.nginx

import com.mzx.nginx.domain.Resource
import com.mzx.nginx.domain.Upstream
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.launch

class ProxyVerticle : CoroutineVerticle() {
    private val portKey = "port"
    private val upstreamKey = "upstream"
    private val resourceKey = "resource"

    private var port = 9000

    private lateinit var upstreamArray: Array<Upstream>

    private lateinit var resourceArray: Array<Resource>

    override suspend fun start() {
        try {
            port = config.getInteger(portKey) ?: port

            // 获取为 JsonObject 或 JsonArray 时, 会进行强转, 提前判断键值是否存在, 避免报错
            upstreamArray = if (config.containsKey(upstreamKey)) {
                config.getJsonArray(upstreamKey).map { Upstream.parse(vertx, it as JsonObject) }
                    .toTypedArray()
            } else emptyArray()

            resourceArray = if (config.containsKey(resourceKey)) {
                config.getJsonArray(resourceKey).map { Resource.parse(vertx, it as JsonObject) }
                    .toTypedArray()
            } else emptyArray()

            val serverOptions = HttpServerOptions().apply {
                isTcpKeepAlive = true
            }

            val server = vertx.createHttpServer(serverOptions)

            // 配置静态资源路由
            val resourceRouter = Router.router(vertx)

            resourceArray.forEach {
                // 路径在匹配时, 必须为 ".../*" 的格式
                // 这里在配置路由时, 对路径进行处理, 保证为 "/../*" 的格式
                resourceRouter.route("${it.prefix.trimEnd('/', '*')}/*")
                    .handler { rc ->
                        if (it.cachingEnabled == false) {
                            rc.response().headers()
                                .add("Cache-Control", "no-store")
                                .add("Cache-Control", "no-cache")
                        }
                        rc.next()
                    }
                    .handler(
                        StaticHandler.create().setAllowRootFileSystemAccess(true)
                            .setWebRoot(it.dir).apply {
                                if (it.cachingEnabled == true) {
                                    setCachingEnabled(it.cachingEnabled)
                                    setMaxAgeSeconds(it.maxAgeSeconds)
                                }
                            }
                    )
            }

            // Router 默认包含 404 处理, 这里修改默认的逻辑, 当 404 时, 跳转至配置中的 reroute404
            // 若 reroute404 资源也不存在, 返回 404 状态码及内容
            resourceRouter.errorHandler(404) { rc ->
                // 当遇到该错误时, 可以保证目标请求静态资源
                val reroute404 = resourceArray.find { rc.request().uri().startsWith(it.prefix) }?.reroute404
                if (reroute404 != rc.request().uri()) {
                    rc.reroute(reroute404)
                } else rc.response().apply {
                    statusCode = 404
                    send("404")
                }
            }

            // 处理 WebSocket 请求
            server.webSocketHandler { webSocket ->
                // 如果找到了可以请求到的代理地址, 建立连接, 否则, 拒绝
                upstreamArray.find { webSocket.uri().startsWith(it.prefix) }?.also { upstream ->
                    // 尝试向服务端建立 WebSocket
                    upstream.client.webSocket(webSocket.uri().substring(upstream.prefix.length))
                        .onSuccess { upstreamWebSocket ->
                            // 建立成功

                            // 转发数据
                            webSocket.pipeTo(upstreamWebSocket)
                            upstreamWebSocket.pipeTo(webSocket)

                            // 一端关闭同时关闭另一端
                            webSocket.closeHandler { upstreamWebSocket.close() }
                            upstreamWebSocket.closeHandler { webSocket.close() }

                        }.onFailure {
                            // 在执行异步操作后, webSocket 就已经接收建立了, 此时只能关闭
                            webSocket.close()
                        }
                } ?: webSocket.reject()
            }

            // 处理 Http 请求
            server.requestHandler { request ->
                val uri = request.uri()
                resourceArray.find { uri.startsWith(it.prefix) }?.also {
                    // 如果匹配到了静态资源路径, 由静态资源路由处理
                    resourceRouter.handle(request)
                } ?: upstreamArray.find { uri.startsWith(it.prefix) }?.also { upstream ->
                    // 如果找到了可以请求到的代理地址, 请求

                    // 异步处理前, 暂停请求处理
                    request.pause()

                    // 启动协程, 处理后续操作
                    launch {
                        try {
                            val upstreamRequest =
                                upstream.client.request(request.method(), uri.substring(upstream.prefix.length)).await()

                            upstreamRequest.headers().setAll(request.headers())
                            val upstreamResponse = upstreamRequest.send(request).await()

                            val response = request.response()
                            // 需要同步状态码, 因为响应不止 200
                            // 该代理是可能代理资源请求的, 而在资源请求过程内, 可能存在 304 响应
                            // 也可能存在其它情况, 为了维持代理的功能, 此处状态码应与上游响应保持一致
                            response.statusCode = upstreamResponse.statusCode()
                            response.headers().setAll(upstreamResponse.headers())
                            response.send(upstreamResponse)

                            upstreamResponse.exceptionHandler { t ->
                                t.printStackTrace()
                                response.statusCode = 500
                                response.end(t.message)
                            }
                        } catch (t: Throwable) {
                            request.response().run {
                                statusCode = 500
                                end("请求失败: ${t.message}")
                            }
                        }
                    }
                } ?: request.response().run {
                    statusCode = 404
                    end("没有找到可以请求的代理地址")
                }
            }

            server.listen(port).await()
            println("Proxy server started success on port $port")
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
