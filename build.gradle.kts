plugins {
    id("org.jetbrains.intellij.platform") version "2.5.0"
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val type = providers.gradleProperty("platformType")
        val version = providers.gradleProperty("platformVersion")
        create(type, version)
        bundledPlugin("org.jetbrains.plugins.terminal")
        instrumentationTools()
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.kiro.intellij"
        name = "Kiro with GUI"
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = "251"
        }
    }
}

kotlin {
    jvmToolchain(21)
}
