import java.util.Properties

plugins {
    alias(libs.plugins.android.baselineprofile)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktfmt)
}

android {
    namespace = "com.goydevv.arseide.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.goydevv.arseide"
        minSdk = 26

        targetSdk = 37

        // versioning
        versionCode = 130
        versionName = "5.0.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging { jniLibs { useLegacyPackaging = true } }

    signingConfigs {
        create("release") {
            val isGitHubActions = System.getenv("GITHUB_ACTIONS") == "true"

            val propertiesFilePath =
                if (isGitHubActions) {
                    "/tmp/signing.properties"
                } else {
                    "/home/rohit/Android/xed-signing/signing.properties"
                }

            val propertiesFile = File(propertiesFilePath)
            var configured = false
            if (propertiesFile.exists()) {
                val properties = Properties()
                properties.load(propertiesFile.inputStream())
                val pKeyAlias = properties["keyAlias"] as String?
                val pKeyPassword = properties["keyPassword"] as String?
                val pStorePassword = properties["storePassword"] as String?
                val pStoreFile =
                    if (isGitHubActions) {
                        File("/tmp/xed.keystore")
                    } else {
                        (properties["storeFile"] as String?)?.let { File(it) }
                    }

                if (
                    !pKeyAlias.isNullOrBlank() &&
                        !pKeyPassword.isNullOrBlank() &&
                        !pStorePassword.isNullOrBlank() &&
                        pStoreFile != null &&
                        pStoreFile.exists()
                ) {
                    keyAlias = pKeyAlias
                    keyPassword = pKeyPassword
                    storeFile = pStoreFile
                    storePassword = pStorePassword
                    configured = true
                }
            }

            if (!configured) {
                println("Release signing secrets not found; falling back to bundled debug testkey.")
                storeFile = file(layout.buildDirectory.dir("../testkey.keystore"))
                storePassword = "testkey"
                keyAlias = "testkey"
                keyPassword = "testkey"
            }
        }
        getByName("debug") {
            storeFile = file(layout.buildDirectory.dir("../testkey.keystore"))
            storePassword = "testkey"
            keyAlias = "testkey"
            keyPassword = "testkey"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isCrunchPngs = false
            resValue("string", "app_name", "Arse IDE")

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            signingConfig = signingConfigs.getByName("release")
        }

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string", "app_name", "Arse IDE Debug")
        }

        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }

        // Arse IDE release build (`com.goydevv.arseide`, label "Arse IDE").
        // Signed with the bundled testkey so every build updates cleanly over the previous install.
        create("pro") {
            initWith(buildTypes.getByName("release"))
            matchingFallbacks += listOf("release")
            resValue("string", "app_name", "Arse IDE")
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
        }
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(libs.androidx.profileinstaller)
    coreLibraryDesugaring(libs.desugar)

    baselineProfile(project(":baselineprofile"))
    implementation(project(":core:main"))
}
