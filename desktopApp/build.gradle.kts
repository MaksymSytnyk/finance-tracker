import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.maksym.financetracker.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Personal Finance Tracker"
            packageVersion = "1.0.0"
            linux { iconFile.set(project.file("src/main/resources/app-icon.png")) }
            windows { iconFile.set(project.file("src/main/resources/app-icon.ico")) }
            macOS { iconFile.set(project.file("src/main/resources/app-icon.icns")) }
        }
    }
}
