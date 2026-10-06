import org.gradle.api.tasks.application.CreateStartScripts

plugins {
    application
}

val lwjglVersion = "3.3.6"

// Natives des trois systèmes : une distribution produite n'importe où fonctionne partout
val lwjglNatives = listOf(
    "natives-linux", "natives-linux-arm64",
    "natives-windows", "natives-windows-arm64",
    "natives-macos", "natives-macos-arm64"
)

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    for (module in listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-openal", "lwjgl-stb")) {
        implementation("org.lwjgl:$module")
        for (natives in lwjglNatives) {
            runtimeOnly("org.lwjgl:$module::$natives")
        }
    }
}

application {
    mainClass.set("outside.Game")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    // -XstartOnFirstThread n'est reconnu que par la JVM macOS : décidé ici, sur la machine qui exécute
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

// Le script Unix généré détecte lui-même macOS au lancement : la distribution reste
// utilisable quel que soit le système qui l'a construite
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        val script = outputDir!!.resolve(applicationName!!)
        val text = script.readText()
        val marker = "DEFAULT_JVM_OPTS="
        val i = text.indexOf(marker)
        check(i >= 0) { "Ligne $marker introuvable dans $script" }
        val eol = text.indexOf('\n', i)
        val inject = """

case "$(uname -s)" in
    Darwin*) DEFAULT_JVM_OPTS="${'$'}DEFAULT_JVM_OPTS -XstartOnFirstThread" ;;
esac
"""
        script.writeText(text.substring(0, eol + 1) + inject + text.substring(eol + 1))
    }
}
