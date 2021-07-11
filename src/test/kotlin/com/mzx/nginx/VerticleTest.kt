package com.mzx.nginx

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpMethod
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.json.jsonObjectOf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class VerticleTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll(vertx: Vertx, testContext: VertxTestContext) {
            // 需要部署服务及代理, 共两个检查点
            // 当检查点都被标记后, 测试应该通过
            // 需要注意: 在执行 completeNow 之类的方法后, 测试依旧会通过
            // 若测试点存在为标记, 测试则会等待超时
            val deployCheckpoint = testContext.checkpoint(2)

            // 配置文件读取
            val configStoreOptions = ConfigStoreOptions().apply {
                type = "file"
                config = jsonObjectOf("path" to "config.json")
            }
            val configRetrieverOptions = ConfigRetrieverOptions().addStore(configStoreOptions)

            // 期望结果成功
            ConfigRetriever.create(vertx, configRetrieverOptions).getConfig(testContext.succeeding { jsonObject ->
                // 在结果成功的基础上, 标记检查点
                vertx.deployVerticle(ServerVerticle(), testContext.succeeding { deployCheckpoint.flag() })
                vertx.deployVerticle(
                    ProxyVerticle(),
                    DeploymentOptions().setConfig(jsonObject),
                    testContext.succeeding { deployCheckpoint.flag() }
                )
            })
        }
    }

    @Test
    fun testServer(vertx: Vertx, testContext: VertxTestContext) {
        val client = vertx.createHttpClient()

        // 共两个请求, 两个检查点
        val requestCheckpoint = testContext.checkpoint(2)

        client.request(HttpMethod.GET, 8080, "127.0.0.1", "/hello")
            .compose { req -> req.send().compose(HttpClientResponse::body) }
            .onComplete(testContext.succeeding { buffer ->
                // 进行断言时, 使用 verify 包裹
                testContext.verify {
                    assertEquals(buffer.toString(), "hello")
                    requestCheckpoint.flag()
                }
            })

        client.request(HttpMethod.POST, 8080, "127.0.0.1", "/hello")
            .compose { req -> req.send(jsonObjectOf("name" to "Tom").toString()).compose(HttpClientResponse::body) }
            .onComplete(testContext.succeeding { buffer ->
                testContext.verify {
                    assertEquals(buffer.toString(), "Tom")
                    requestCheckpoint.flag()
                }
            })
    }

    @Test
    fun testProxyServer(vertx: Vertx, testContext: VertxTestContext) {
        val client = vertx.createHttpClient()

        val requestCheckpoint = testContext.checkpoint(3)

        client.request(HttpMethod.GET, 9000, "127.0.0.1", "/a/hello")
            .compose { req -> req.send().compose(HttpClientResponse::body) }
            .onComplete(testContext.succeeding { buffer ->
                testContext.verify {
                    assertEquals(buffer.toString(), "hello")
                    requestCheckpoint.flag()
                }
            })

        client.request(HttpMethod.POST, 9000, "127.0.0.1", "/a/hello")
            .compose { req -> req.send(jsonObjectOf("name" to "Mary").toString()).compose(HttpClientResponse::body) }
            .onComplete(testContext.succeeding { buffer ->
                testContext.verify {
                    assertEquals(buffer.toString(), "Mary")
                    requestCheckpoint.flag()
                }
            })

        // 没有可以请求的代理地址
        client.request(HttpMethod.GET, 9000, "127.0.0.1", "/b/hello")
            .compose { req -> req.send() }
            .onComplete(testContext.succeeding { response ->
                // 不进行 body 转换, 校验状态码
                testContext.verify {
                    assertEquals(response.statusCode(), 500)
                    requestCheckpoint.flag()
                }
            })
    }

    @Test
    fun testResourceProxy(vertx: Vertx, testContext: VertxTestContext) {
        val client = vertx.createHttpClient()

        val requestCheckpoint = testContext.checkpoint(5)

        client.request(HttpMethod.GET, 9000, "127.0.0.1", "/static1/index.html")
            .compose { req -> req.send() }
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    assertEquals(response.statusCode(), 200)
                    requestCheckpoint.flag()
                }
            })

        client.request(HttpMethod.GET, 9000, "127.0.0.1", "/static2/index.html")
            .compose { req -> req.send() }
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    assertEquals(response.statusCode(), 200)
                    requestCheckpoint.flag()
                }
            })

        client.request(HttpMethod.GET, 9000, "127.0.0.1", "/static1/hint.txt")
            .compose { req -> req.send() }
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    assertEquals(response.statusCode(), 200)
                    requestCheckpoint.flag()
                }
            })

        client.request(HttpMethod.GET, 9000, "127.0.0.1", "/static1/notfound.html")
            .compose { req -> req.send() }
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    assertEquals(response.statusCode(), 301)
                    requestCheckpoint.flag()
                }
            })

        client.request(HttpMethod.GET, 9000, "127.0.0.1", "/static2/notfound.html")
            .compose { req -> req.send() }
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    assertEquals(response.statusCode(), 404)
                    requestCheckpoint.flag()
                }
            })
    }

    @Test
    fun testWebSocketProxy(vertx: Vertx, testContext: VertxTestContext) {
        val client = vertx.createHttpClient()
        // 5 个消息 + 1 个连接断开
        val requestCheckpoint = testContext.checkpoint(6)

        client.webSocket(9000, "127.0.0.1", "/a", testContext.succeeding { webSocket ->
            webSocket.handler { buffer ->
                testContext.verify {
                    assertTrue(buffer.toString().endsWith("message"))
                    requestCheckpoint.flag()
                }
            }
            webSocket.endHandler { requestCheckpoint.flag() }
            // 发送五个消息
            for (i in 0..5) {
                webSocket.write(Buffer.buffer("is $i message"))
            }
        })
        // 这里并没有主动关闭 webSocket, 但是依旧得到了连接断开
        // 推测: 在执行完当前方法后, webSocket 自动断开
    }
}
