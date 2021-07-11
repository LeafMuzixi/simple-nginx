package com.mzx.nginx.domain

import io.vertx.core.json.JsonObject
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import java.net.URL

class Upstream(private val vertx: Vertx, val url: String, var weight: Int) {
    companion object {
        fun parse(vertx: Vertx, json: JsonObject): Upstream {
            return Upstream(
                vertx,
                url = json.getString("url"),
                weight = json.getInteger("weight", 1)
            )
        }
    }

    val client: HttpClient = URL(url).let { it ->
        val host = it.host
        val port = it.port
        val clientOptions = HttpClientOptions().also {
            it.defaultHost = host
            it.defaultPort = port
        }
        vertx.createHttpClient(clientOptions)
    }

}
