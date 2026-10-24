plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Signing credentials belong in user-local Gradle properties or environment,
// never in Git or in a generated, shared keystore.
val releaseStoreFile = providers.gradleProperty("edgeflowReleaseStoreFile").orNull
    ?: System.getenv("EDGEFLOW_RELEASE_STORE_FILE")
val releaseStorePassword = providers.gradleProperty("edgeflowReleaseStorePassword").orNull
    ?: System.getenv("EDGEFLOW_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.gradleProperty("edgeflowReleaseKeyAlias").orNull
    ?: System.getenv("EDGEFLOW_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.gradleProperty("edgeflowReleaseKeyPassword").orNull
    ?: System.getenv("EDGEFLOW_RELEASE_KEY_PASSWORD")
val hasReleaseCredentials = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.edgeflow"
    // libxposed service 102.0.0 ships an AAR that requires Android API 37.
    // This changes only compilation; minSdk/targetSdk remain unchanged.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.edgeflow"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseCredentials) {
            create("edgeflowRelease") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Never silently sign a release with the debug key. If credentials
            // are unavailable the result is UNSIGNED and not publishable.
            signingConfig = signingConfigs.findByName("edgeflowRelease")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            // The hook code reads framework classes that do not exist in the
            // public SDK, so reflection-only access must not be an error.
            freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Framework hook API is supplied by LSPosed at injection time.
    compileOnly(libs.libxposed.api)
    // XposedProvider is a regular Android ContentProvider instantiated in the
    // app process before Application.onCreate. It MUST be bundled in the APK;
    // compileOnly causes a ClassNotFoundException and crash on every launch.
    implementation(libs.libxposed.service)

    testImplementation(kotlin("test"))
}
