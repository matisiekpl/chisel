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
        bundledPlugin("com.intellij.database")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.mateuszwozniak.chisel"
        name = "Chisel"
        version = project.version.toString()
        description = """
            Chisel runs the Claude Code CLI installed on your machine and gives it a panel inside the IDE.

            <ul>
              <li>Conversations in tool window tabs, with the transcript, queued messages and image or file attachments.</li>
              <li>Ask, Plan, Implementation and Auto modes, with the model and effort level chosen per conversation.</li>
              <li>Every file write arrives as a diff you accept, comment on line by line, or edit by hand.</li>
              <li>Shell commands and agent questions are answered in dedicated dialogs.</li>
              <li>Subagents and workflows are tracked live, with their output available per agent.</li>
              <li>Context occupancy, token counts and session cost are shown next to the prompt.</li>
              <li>In IDEs with the Database plugin, the DDL of a data source schema attaches to a prompt.</li>
              <li>Sessions started in the terminal are picked up, and a conversation can be driven from a phone.</li>
            </ul>

            Chisel is not affiliated with Anthropic. It requires Claude Code to be installed and signed in
            on this machine, and it uses your own Claude subscription through that CLI.
        """.trimIndent()

        changeNotes = """
            <ul>
              <li>First release.</li>
            </ul>
        """.trimIndent()
        vendor {
            name = "Mateusz Wozniak"
            email = "mateusz@harmonyze.com"
            url = "https://mateuszwozniak.com"
        }
        ideaVersion {
            sinceBuild = "253"
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
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
