import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = "com.mateuszwozniak"
version = "0.2.2"

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
            Chisel is an AI coding agent for people who care about the quality of what lands in the
            repository. Every change it proposes goes through a review you control: the whole file in a
            diff, a comment on any line, and the freedom to rewrite the agent's code by hand before a
            single byte touches disk. Your corrections go back to the agent and shape the rest of the work.

            <p>Chisel runs on your existing Claude Code subscription, driving the Claude Code CLI already
            signed in on your machine. No separate account, no API key, no extra billing.</p>

            <ul>
              <li>Every write opens in the IDE diff: accept it, comment line by line like a pull request, or rewrite the proposal by hand.</li>
              <li>A correction carries forward, so a name or a convention you fixed once stays fixed in the files that follow.</li>
              <li>Plans, shell commands and agent questions arrive as dialogs you answer, not as things that already happened.</li>
              <li>Ask, Plan, Implementation and Auto modes decide how much the agent may do on its own; the model and effort level are picked per conversation.</li>
              <li>A message can be rewound together with the files it changed.</li>
              <li>Conversations started in the terminal continue in the IDE, and any conversation can be handed to a phone.</li>
              <li>Files, images and, in IDEs with the Database plugin, the DDL of a data source schema attach to a prompt.</li>
              <li>Context usage, token counts and the cost of the conversation sit next to the input.</li>
            </ul>

            Chisel is not affiliated with Anthropic. It requires the Claude Code CLI installed and signed
            in on this machine.
        """.trimIndent()

        changeNotes = """
            <ul>
              <li>The prompt input paints one surface: the editor area no longer sits as a lighter box inside the card.</li>
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
