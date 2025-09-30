plugins {
    java
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(20))
    }
}

repositories {
    mavenCentral()
}

// Detect OS for JavaFX classifier
val osName = System.getProperty("os.name").lowercase()
val platform = when {
    osName.contains("win") -> "win"
    osName.contains("mac") -> "mac"
    osName.contains("linux") -> "linux"
    else -> throw GradleException("Unknown OS: $osName")
}

val javafxVersion = "20.0.2"

dependencies {
    implementation("org.fxmisc.richtext:richtextfx:0.10.9") // latest version
    implementation("org.openjfx:javafx-base:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-fxml:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-media:$javafxVersion:$platform")
}

application {
    mainClass.set("manager.CodeSnippetManagerFX")
}

tasks.withType<JavaExec> {
    jvmArgs = listOf(
        "--module-path", configurations.runtimeClasspath.get().asPath,
        "--add-modules", "javafx.controls,javafx.fxml"
    )
}
