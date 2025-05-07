import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    idea
    java
}

group = "li.nux.hippo"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.charleskorn.kaml:kaml:0.77.1")
    implementation("com.akuleshov7:ktoml-core:0.5.1")
    implementation("com.akuleshov7:ktoml-file:0.5.1")
    implementation("com.github.ajalt.clikt:clikt:5.0.3")
    implementation("io.github.microutils:kotlin-logging-jvm:2.0.11")
    implementation("com.drewnoakes:metadata-extractor:2.19.0")
    implementation("net.coobird:thumbnailator:0.4.20")
    implementation("org.imgscalr:imgscalr-lib:4.2")
    implementation("org.apache.tika:tika-core:3.1.0")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    testImplementation(kotlin("test"))
}

val mainClass = "li.nux.hippo.HippoKt"

tasks.processResources {
    expand("version" to project.version)
}

sourceSets {
    sourceSets.main {
        java {
            srcDir(file("build/generated/main/kotlin/"))
        }
    }
}

idea.module {
    generatedSourceDirs.add(file("build/generated/main/kotlin/"))
}

val fatJar = task("fatJar", Jar::class) {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to mainClass)
    }
    from(configurations.runtimeClasspath.get()
        .onEach { println("add from dependencies: ${it.name}") }
        .map { if (it.isDirectory) it else zipTree(it) })
    val sourcesMain = sourceSets.main.get()

    sourcesMain.allSource.forEach { println("add from sources: ${it.name}") }
    from(sourcesMain.output)
    from(sourcesMain.java.srcDirs)
}

tasks.register("generateVersionObject") {
    val versionObjectFile = file("build/generated/main/kotlin/li/nux/hippo/VersionData.kt")
    outputs.file(versionObjectFile)
    val bv = project.version
    doLast {
        versionObjectFile.writeText("""
            package li.nux.hippo

            internal object VersionData {
                val version: String = "$bv"
            }
        """.trimIndent())
    }
}.let { generateVersionTask ->
    tasks.withType<KotlinCompile>().configureEach {
        dependsOn(generateVersionTask)
    }

    tasks.named("build") {
        dependsOn(generateVersionTask)
    }
}

tasks {
    "build" {
        dependsOn(fatJar)
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
//    jvmToolchain(21)

}

