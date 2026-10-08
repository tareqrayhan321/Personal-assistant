import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Secrets/config live in local.properties (git-ignored), never in source control.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun prop(name: String, default: String = "") = localProps.getProperty(name, default)

// Where "Report this response" posts to: REPORT_URL, else <PROXY_BASE_URL>/v1/report, else not configured.
fun reportUrl(): String =
    prop("REPORT_URL").ifBlank { prop("PROXY_BASE_URL").trimEnd('/').takeIf { it.isNotBlank() }?.let { "$it/v1/report" } ?: "" }

android {
    namespace = "com.personalmentor.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.personalmentor.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "LLM_BASE_URL", "\"${prop("LLM_BASE_URL", "https://api.openai.com/")}\"")
        buildConfigField("String", "LLM_API_KEY", "\"${prop("LLM_API_KEY")}\"")
        buildConfigField("String", "LLM_MODEL", "\"${prop("LLM_MODEL", "gpt-4o-mini")}\"")
        buildConfigField("String", "EMBEDDING_MODEL", "\"${prop("EMBEDDING_MODEL", "text-embedding-3-small")}\"")
        buildConfigField("String", "REPORT_URL", "\"${reportUrl()}\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${prop("PRIVACY_POLICY_URL")}\"")
        buildConfigField("String", "REPORT_TOKEN", "\"${prop("PROXY_CLIENT_TOKEN")}\"")
        // Offline demo responder unless an API key or a custom base URL (e.g. local Ollama) is configured.
        buildConfigField(
            "boolean",
            "USE_FAKE_LLM",
            (prop("LLM_API_KEY").isBlank() && prop("LLM_BASE_URL").isBlank()).toString(),
        )
    }

    signingConfigs {
        // Optional: set RELEASE_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD in local.properties.
        create("release") {
            prop("RELEASE_STORE_FILE").takeIf { it.isNotBlank() }?.let {
                storeFile = rootProject.file(it)
                storePassword = prop("RELEASE_STORE_PASSWORD")
                keyAlias = prop("RELEASE_KEY_ALIAS")
                keyPassword = prop("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // The APK never carries a provider key: requests go to your proxy (server/), authenticated by
            // a revocable client token. LLM_API_KEY / LLM_BASE_URL from local.properties are debug-only.
            buildConfigField("String", "LLM_BASE_URL", "\"${prop("PROXY_BASE_URL", "https://api.openai.com/")}\"")
            buildConfigField("String", "LLM_API_KEY", "\"${prop("PROXY_CLIENT_TOKEN")}\"")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// A release build without a proxy would ship an app that cannot reach an AI backend or send reports.
// CI passes -PciBuild=true to compile/shrink-check without secrets.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        val missing = listOf("PROXY_BASE_URL", "PROXY_CLIENT_TOKEN", "PRIVACY_POLICY_URL").filter { prop(it).isBlank() }
        if (!project.hasProperty("ciBuild") && missing.isNotEmpty()) {
            throw GradleException("Release builds need ${missing.joinToString()} in local.properties (see docs/RELEASE.md).")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (versions managed by the BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    // Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Local storage
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
