import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName = project.property("archives_base_name") as String
}

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net/")
    maven("https://maven.fabricmc.net/")
    maven("https://maven.parchmentmc.org") {
        content { includeGroup("org.parchmentmc.data") }
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    minecraft(libs.minecraft)
    mappings(@Suppress("UnstableApiUsage") loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${libs.versions.minecraft.get()}:${libs.versions.parchment.get()}@zip")
    })
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)
    modImplementation(libs.fabric.language.kotlin)
}

tasks {
    processResources {
        inputs.property("version", version)
        inputs.property("minecraft_version", libs.versions.minecraft.get())
        inputs.property("kotlin_loader_version", libs.versions.fabric.language.kotlin.get())
        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(
                "version" to inputs.properties["version"] as String,
                "minecraft_version" to inputs.properties["minecraft_version"] as String,
                "kotlin_loader_version" to inputs.properties["kotlin_loader_version"] as String,
            )
        }
    }
    compileJava {
        options.encoding = "UTF-8"
        options.release = 21
    }
    withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }
    jar {
        from("LICENSE", "NOTICE")
    }
    test {
        useJUnitPlatform()
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
