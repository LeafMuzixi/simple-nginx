plugins {
    kotlin("jvm") version "1.5.20"
    application
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "com.mzx"
version = "1.0-SNAPSHOT"

val mainVerticleName = "com.mzx.nginx.ServerVerticle"
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

repositories {
    mavenCentral()
}

application {
    mainClass.set(launcherClassName)
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.vertx:vertx-web:${Version.vertx}")
    implementation("io.vertx:vertx-web-client:${Version.vertx}")
    implementation("io.vertx:vertx-lang-kotlin:${Version.vertx}")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:${Version.vertx}")
    testImplementation(kotlin("test"))
    testImplementation("io.vertx:vertx-junit5:${Version.vertx}")
    testImplementation("org.junit.jupiter:junit-jupiter:${Version.junitJupiter}")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveClassifier.set("fat")
    manifest {
        attributes(mapOf("Main-Verticle" to mainVerticleName))
    }
    mergeServiceFiles()
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
        )
    }
}

tasks.withType<JavaExec> {
    args = listOf(
        "run",
        mainVerticleName,
        "--redeploy=$watchForChange",
        "--launcher-class=$launcherClassName",
        "--on-redeploy=$doOnChange"
    )
}

tasks.wrapper {
    gradleVersion = "7.1.1"
    distributionType = Wrapper.DistributionType.ALL
}
