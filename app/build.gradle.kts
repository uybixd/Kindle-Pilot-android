import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.exists()) {
        signingPropertiesFile.inputStream().use(::load)
    }
}
val releaseStoreFile = providers.environmentVariable("KPILOT_KEYSTORE_PATH").orNull
    ?: signingProperties.getProperty("storeFile")
val releaseStorePassword = providers.environmentVariable("KPILOT_KEYSTORE_PASSWORD").orNull
    ?: signingProperties.getProperty("storePassword")
val releaseKeyAlias = providers.environmentVariable("KPILOT_KEY_ALIAS").orNull
    ?: signingProperties.getProperty("keyAlias")
val releaseKeyPassword = providers.environmentVariable("KPILOT_KEY_PASSWORD").orNull
    ?: signingProperties.getProperty("keyPassword")
val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val hasAnyReleaseSigningValue = releaseSigningValues.any { !it.isNullOrBlank() }
val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }
val xiaomiWearableSdk = project.file("libs/xms-wearable-lib_1.4_release.aar")

check(!hasAnyReleaseSigningValue || hasReleaseSigning) {
    "Release signing is incomplete. Set every KPILOT_KEY* environment variable or all keystore.properties fields."
}

check(xiaomiWearableSdk.isFile) {
    """
    Xiaomi wearable SDK not found: ${xiaomiWearableSdk.absolutePath}
    Download Xiaomi's official interconnect development demo and copy
    xms-wearable-lib_1.4_release.aar to app/libs/.
    See app/libs/README.md for the official URL and expected SHA-256.
    """.trimIndent()
}

android {
    namespace = "com.hongxiang.kpilot"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.hongxiang.kpilot"
        minSdk = 24
        targetSdk = 37
        versionCode = 14
        versionName = "1.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("localRelease") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Keep the test build installable next to the user's existing KPilot.
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("localRelease")
            }
            optimization {
                // The extended Compose icon artifact contains thousands of icons. R8 keeps
                // only the icons and code paths used by KPilot, which also avoids Android
                // generating large optimized DEX artifacts after the app has been installed.
                enable = true
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets.getByName("main").assets.directories.add(rootProject.file("Donation_QR_Code").absolutePath)
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val buildTypeName = variant.buildType ?: variant.name
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                output.versionName.map { versionName ->
                    "KPilot-$versionName-$buildTypeName.apk"
                },
            )
        }
    }
}

dependencies {
    implementation(files(xiaomiWearableSdk))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsch)
    implementation(libs.androidx.exifinterface)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
