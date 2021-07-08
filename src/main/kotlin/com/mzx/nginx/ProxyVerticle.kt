package com.mzx.nginx

import com.mzx.nginx.domain.Upstream
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.launch

class ProxyVerticle : CoroutineVerticle() {
    private val port = 9000

    private lateinit var upstreamArray: Array<Upstream>

    override suspend fun start() {
        try {
            upstreamArray = config.getJsonArray("upstream").map { Upstream.parse(vertx, it as JsonObject) }
                .toTypedArray()

            val serverOptions = HttpServerOptions().apply {
                isTcpKeepAlive = true
            }

            val server = vertx.createHttpServer(serverOptions)

            server.requestHandler { request ->
                upstreamArray.find { request.uri().startsWith(it.prefix) }?.also { upstream ->
                    // 如果找到了可以请求到的代理地址, 请求

                    // 暂停数据处理
                    request.pause()

                    // 启动协程, 处理后续操作
                    launch {
                        try {
                            val uri = request.uri().replace(upstream.prefix, "/")
                            val upstreamRequest = upstream.client.request(request.method(), uri).await()

                            upstreamRequest.headers().setAll(request.headers())
                            val upstreamResponse = upstreamRequest.send(request).await()

                            val response = request.response()
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
