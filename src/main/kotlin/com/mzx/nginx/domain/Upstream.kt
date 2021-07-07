package com.mzx.nginx.domain

import io.vertx.core.json.JsonObject
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import java.net.URL

class Upstream(private val vertx: Vertx, val prefix: String, val url: String) {
    companion object {
        fun parse(vertx: Vertx, json: JsonObject): Upstream {
            return Upstream(
                vertx,
                prefix = json.getString("prefix"),
                url = json.getString("url"),
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
