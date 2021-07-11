package com.mzx.nginx.domain

import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.json.JsonObject
import kotlin.random.Random
import kotlin.random.nextInt

class Server(
    private val vertx: Vertx,
    val prefix: String,
    val upstreamArray: Array<Upstream>
) {
    companion object {
        fun parse(vertx: Vertx, json: JsonObject): Server {
            return Server(
                vertx,
                prefix = json.getString("prefix"),
                upstreamArray = json.getJsonArray("upstream").mapNotNull {
                    if (it is JsonObject) {
                        Upstream.parse(vertx, it)
                    } else null
                }.toTypedArray()
            )
        }
    }

    // 轮询队列
//    val queue = upstreamArray.toMutableList()

    fun getClient(): HttpClient {
        // 轮询
        // 这里利用队列提供轮询机制, 每次获取 client 时, 移除队头, 加入队尾, 返回它的 client
//        val nextUpstream = queue.removeFirst()
//        queue.add(nextUpstream)
//        return nextUpstream.client

        // 权重算法, 因为代理服务不会很多, 这里直接线性查找
        val totalWeight = upstreamArray.sumOf { it.weight }
        var targetWeight = Random.nextInt(0..totalWeight)
        for (upstream in upstreamArray) {
            if (targetWeight <= upstream.weight) {
                return upstream.client
            }
            targetWeight -= upstream.weight
        }

        // 权重算法在前, 逻辑上不会执行到这里, 以下随机选择作担保
        // 随机
        return upstreamArray.random().client
    }
}
