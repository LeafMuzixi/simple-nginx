package com.mzx.nginx

import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions
import io.vertx.ext.web.handler.sockjs.SockJSSocket
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await


class ServerVerticle : CoroutineVerticle() {
    private val port = 8080

    override suspend fun start() {
        try {
            val server = vertx.createHttpServer()

            // WebSocket 处理
            // 该方式将锁定 ws 协议, 无视路径, 若存在 sockjs, 则冲突
            // sockjs 也会建立 webSocket 连接(使用固定格式的路径)
//            server.webSocketHandler { webSocket ->
//                val id = webSocket.binaryHandlerID()
//                println("$id 已连接")
//                webSocket.handler { buffer ->
//                    // 仅将收到的消息包装并返回
//                    val message = "I received your message: $buffer"
//                    webSocket.writeBinaryMessage(Buffer.buffer(message))
//                }.closeHandler {
//                    println("$id 已断开")
//                }
//            }

            val router = Router.router(vertx)

            // 配置 WebSocket 端点来指定升级 WebSocket
            router.route("/websocket").handler { rc ->
                if (rc.request().getHeader("Upgrade").equals("websocket")) {
                    // 若该端点请求存在升级 websocket 请求头, 则升级
                    rc.request().toWebSocket().onSuccess { webSocket ->
                        val id = webSocket.binaryHandlerID()
                        println("$id 已连接")
                        webSocket.handler { buffer ->
                            // 仅将收到的消息包装并返回
                            val message = "I received your message: $buffer"
                            webSocket.writeBinaryMessage(Buffer.buffer(message))
                        }.closeHandler {
                            println("$id 已断开")
                        }
                    }.onFailure {
                        // 返回错误信息, 返回内容及状态码无影响
                        rc.response().end(it.message)
                    }
                } else {
                    // 否则, 直接返回, 返回内容及状态码无影响
                    rc.response().end()
                }
            }

            // SockJS 配置
            // 设置心跳间隔
            val options = SockJSHandlerOptions().setHeartbeatInterval(2000)
            // 每个 SockJS 应用都应该使用 SockJSHandler.create 自己的 SockJSHandler
            val sockJSHandler = SockJSHandler.create(vertx, options)

            router.mountSubRouter("/sock", sockJSHandler.socketHandler { sockJSSocket: SockJSSocket ->
                // 回显数据
                sockJSSocket.handler(sockJSSocket::write)
            })

            // 处理 webroot 内静态资源
            router.route().handler(StaticHandler.create())

            router.get("/hello").handler { rc ->
                val request = rc.request()
                val response = rc.response()
                response.end("hello")
            }

            router.post("/hello")
                .handler(BodyHandler.create())
                .handler { rc ->
                    val bodyAsJson = rc.bodyAsJson
                    rc.response().end(bodyAsJson.getString("name"))
                }

            router.errorHandler(500) { rc ->
                rc.failure().printStackTrace()
                rc.response().end("发生了错误: ${rc.failure().message}")
            }

            server.requestHandler(router).listen(port).await()
            println("Server started success on port $port")
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
