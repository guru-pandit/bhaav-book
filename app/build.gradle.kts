plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Release signing is driven entirely by the environment, so CI can sign from
 * GitHub secrets and a developer's clone needs no setup at all.
 *
 * With no keystore present the release build still compiles and shrinks — it
 * just comes out unsigned, which keeps `assembleRelease` useful as a check that
 * R8 has not stripped something the app needs.
 */
val releaseKeystore = rootProject.file("release.keystore")
val keystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
val hasReleaseSigning = releaseKeystore.exists() && !keystorePassword.isNullOrBlank()

android {
    namespace = "com.bhaavbook.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bhaavbook.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = keystorePassword
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "bhaavbook"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    lint {
        // A lint regression should fail the build, not scroll past in a log.
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        // Our own code only: a dependency's lint findings are not ours to fix,
        // and letting them fail the build makes upgrades unpredictable.
        checkDependencies = false
        // Sub-3-character symbols like "₹" trip this, and the app is not
        // translated yet — the strings are externalised, which is the point.
        disable += listOf("MissingTranslation", "TypographyEllipsis")
    }

    testOptions {
        unitTests {
            // Lets plain JUnit tests touch android.* stubs without Robolectric.
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }

    // Keeps the APK byte-for-byte comparable between builds of the same commit.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

/**
 * Room writes its schema JSON here on every build. Committing those files is
 * what makes a future migration reviewable: the diff shows exactly what changed
 * in the shopkeeper's database.
 */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.opencsv)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
