plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
    implementation("org.json:json:20230618") // Latest stable as of now
    implementation("org.openjfx:javafx-base:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-fxml:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-media:$javafxVersion:$platform")
}

application {
    mainClass.set("manager.CodeSnippetManagerFX")
}

tasks.shadowJar {
    archiveBaseName.set("CodeManager")
    archiveClassifier.set("")
    archiveVersion.set("1.0.0")

    manifest {
        attributes(
            "Main-Class" to "manager.CodeSnippetManagerFX"
        )
    }
}

// Custom task to create Windows installer
tasks.register("createInstaller") {
    dependsOn("shadowJar")
    group = "distribution"
    description = "Creates a Windows MSI installer using jpackage"

    doLast {
        val appName = "CodeManager"
        val appVersion = "1.0.0"
        val shadowJar = tasks.shadowJar.get().archiveFile.get().asFile

        // Create input directory with the fat JAR
        val inputDir = File(buildDir, "jpackage-input")
        inputDir.deleteRecursively() // Clean old files
        inputDir.mkdirs()

        // Copy the fat JAR
        copy {
            from(shadowJar)
            into(inputDir)
        }

        // Icon path
        val iconPath = File(projectDir, "resources/codemanager.ico")

        val jpackageCmd = mutableListOf(
            "jpackage",
            "--type", "msi",
            "--input", inputDir.absolutePath,
            "--dest", File(buildDir, "installer").absolutePath,
            "--name", appName,
            "--app-version", appVersion,
            "--main-jar", shadowJar.name,
            "--main-class", "manager.CodeSnippetManagerFX",
            "--vendor", "Prodexa",
            "--description", "Code Snippet Manager",
            "--win-dir-chooser",
            "--win-menu",
            "--win-shortcut"
        )

        // Add icon if it exists
        if (iconPath.exists()) {
            jpackageCmd.addAll(listOf("--icon", iconPath.absolutePath))
        }

        // Run jpackage
        exec {
            commandLine(jpackageCmd)
        }

        println("MSI installer created in build/installer/")
    }
}

// Custom task to create Windows EXE installer using fat JAR
tasks.register("createExeInstaller") {
    dependsOn("shadowJar")
    group = "distribution"
    description = "Creates a Windows EXE installer using jpackage"

    doLast {
        val appName = "CodeManager"
        val appVersion = "1.0.0"
        val shadowJar = tasks.shadowJar.get().archiveFile.get().asFile

        // Create input directory with the fat JAR
        val inputDir = File(buildDir, "jpackage-input")
        inputDir.deleteRecursively() // Clean old files
        inputDir.mkdirs()

        // Copy the fat JAR
        copy {
            from(shadowJar)
            into(inputDir)
        }

        // Icon path
        val iconPath = File(projectDir, "resources/codemanager.ico")

        val jpackageCmd = mutableListOf(
            "jpackage",
            "--type", "exe",
            "--input", inputDir.absolutePath,
            "--dest", File(buildDir, "installer").absolutePath,
            "--name", appName,
            "--app-version", appVersion,
            "--main-jar", shadowJar.name,
            "--main-class", "manager.CodeSnippetManagerFX",
            "--vendor", "Prodexa",
            "--description", "Code Snippet Manager",
            "--win-dir-chooser",
            "--win-menu",
            "--win-shortcut"
        )

        // Add icon if it exists
        if (iconPath.exists()) {
            jpackageCmd.addAll(listOf("--icon", iconPath.absolutePath))
        }

        // Run jpackage
        exec {
            commandLine(jpackageCmd)
        }

        println("EXE installer created in build/installer/")
    }
}

tasks.withType<JavaExec> {
    jvmArgs = listOf(
        "--module-path", configurations.runtimeClasspath.get().asPath,
        "--add-modules", "javafx.controls,javafx.fxml"
    )
}
