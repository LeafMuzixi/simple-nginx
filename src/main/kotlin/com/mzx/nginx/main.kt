package com.mzx.nginx

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.DeploymentOptions
import io.vertx.core.Launcher
import io.vertx.core.Vertx
import io.vertx.kotlin.core.json.jsonObjectOf

fun main(args: Array<String>) {
    Launcher.main(args)

//    val vertx = Vertx.vertx();
//    // 配置文件读取
//    val configStoreOptions = ConfigStoreOptions().apply {
//        type = "file"
//        config = jsonObjectOf("path" to "config.json")
//    }
//    val configRetrieverOptions = ConfigRetrieverOptions().addStore(configStoreOptions)
//
//    // 期望结果成功
//    ConfigRetriever.create(vertx, configRetrieverOptions).config.onSuccess { jsonObject ->
//        // 在结果成功的基础上, 标记检查点
//        vertx.deployVerticle(ServerVerticle())
//        vertx.deployVerticle(
//            ProxyVerticle(),
//            DeploymentOptions().setConfig(jsonObject)
//        )
//    }
}
