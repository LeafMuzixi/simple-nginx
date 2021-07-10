package com.mzx.nginx.domain

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject

class Resource(private val vertx: Vertx, val prefix: String, val dir: String, val reroute404: String?) {
    companion object {
        fun parse(vertx: Vertx, json: JsonObject): Resource {
            return Resource(
                vertx,
                prefix = json.getString("prefix"),
                dir = json.getString("dir"),
                reroute404 = json.getString("reroute404"),
            )
        }
    }
}
