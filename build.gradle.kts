import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = "com.mateuszwozniak"
version = "0.1.0"

dependencies {
    intellijPlatform {
        intellijIdea("2025.3")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.mateuszwozniak.chisel"
        name = "Chisel"
        version = project.version.toString()
        description = "AI agent panel that runs the Claude Code CLI installed on your machine."
        vendor {
            name = "Mateusz Wozniak"
            email = "mateusz@harmonyze.com"
        }
        ideaVersion {
            sinceBuild = "253"
            untilBuild = provider { null }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
