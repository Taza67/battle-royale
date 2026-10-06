plugins {
    application
}

val lwjglVersion = "3.3.6"

val lwjglNatives: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch")
    val arm = arch.startsWith("aarch64") || arch.startsWith("arm")
    when {
        os.contains("win") -> if (arm) "natives-windows-arm64" else "natives-windows"
        os.contains("mac") -> if (arm) "natives-macos-arm64" else "natives-macos"
        else -> if (arm) "natives-linux-arm64" else "natives-linux"
    }
}

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    for (module in listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-openal", "lwjgl-stb")) {
        implementation("org.lwjgl:$module")
        runtimeOnly("org.lwjgl:$module::$lwjglNatives")
    }
}

application {
    mainClass.set("outside.Game")
    if (lwjglNatives.startsWith("natives-macos")) {
        applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
