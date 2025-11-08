plugins {alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp) // Wichtig: KSP hier hinzufügen!
}

android {
    namespace = "org.fx.timetracker"
    compileSdk = 36 // API 36 ist eine zukünftige Version, nutze die aktuell stabile Version 34.

    defaultConfig {
        applicationId = "org.fx.timetracker"
        minSdk = 26
        targetSdk = 36 // targetSdk sollte zur compileSdk passen.

        versionCode = 1
        versionName = "1.0.0.24"

        // Wichtig für Jetpack Compose
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // JavaVersion 1.8 oder 17 ist üblicher für Android Compose Projekte
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions { // kotlinOptions statt kotlin { jvmToolchain(...) } ist gängiger
        jvmTarget = "1.8"
    }

    // === WICHTIG: buildFeatures für Compose HINZUFÜGEN ===
    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Bestehende Abhängigkeiten
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom)) // BOM als platform()
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // === COMPOSE ABHÄNGIGKEITEN HINZUFÜGEN ===

    // Compose Bill of Materials (BOM) - verwaltet die Versionen aller Compose-Bibliotheken
    // Damit stellst du sicher, dass alle Compose-Module zueinander passen.
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // === DIES IST DIE FEHLENDE ABHÄNGIGKEIT FÜR 'isSystemInDarkTheme' ===
    implementation("androidx.compose.foundation:foundation")

    // Android-JUnit-Runner für UI-Tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Debug-Implementierungen
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // === ROOM ABHÄNGIGKEITEN HINZUFÜGEN ===
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler) // ksp() statt implementation() für den Compiler

}
