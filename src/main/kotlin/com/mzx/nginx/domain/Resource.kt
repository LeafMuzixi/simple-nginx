package com.mzx.nginx.domain

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

class Resource(
    private val vertx: Vertx,
    val prefix: String, val dir: String,
    val reroute404: String?,
    // 为 null 时, 使用默认配置, 为 true 时, 允许缓存, 为 false 时, 强制不缓存
    val cachingEnabled: Boolean?,
    val maxAgeSeconds: Long
) {
    companion object {
        fun parse(vertx: Vertx, json: JsonObject): Resource {
            return Resource(
                vertx,
                prefix = json.getString("prefix"),
                dir = json.getString("dir"),
                reroute404 = json.getString("reroute404"),
                cachingEnabled = json.getBoolean("cachingEnabled"),
                maxAgeSeconds = json.getLong("maxAgeSeconds", 24 * 60 * 60)
            )
        }
    }
}
