plugins {
    application
}

val tomcatVersion = "9.0.122"

dependencies {
    implementation("org.apache.tomcat.embed:tomcat-embed-core:$tomcatVersion")
    implementation("org.apache.tomcat.embed:tomcat-embed-websocket:$tomcatVersion")
    implementation("com.google.code.gson:gson:2.11.0")
}

application {
    mainClass.set("communication.ServerLauncher")
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir
}

distributions {
    main {
        contents {
            from("src/main/webapp") {
                into("webapp")
            }
        }
    }
}
