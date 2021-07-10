package com.mzx.nginx

import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await

class ServerVerticle : CoroutineVerticle() {
    private val port = 8080

    override suspend fun start() {
        try {
            val server = vertx.createHttpServer()

            // webSocket 处理
            server.webSocketHandler { webSocket ->
                val id = webSocket.textHandlerID()
                println("$id 已连接")
                webSocket.handler { buffer ->
                    // 仅将收到的消息包装并返回
                    val message = "I received your message: $buffer"
                    webSocket.writeTextMessage(message)
                }.closeHandler {
                    println("$id 已断开")
                }
            }

            val router = Router.router(vertx)

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
