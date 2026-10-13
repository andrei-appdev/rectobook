import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The upload key's whereabouts and passwords, kept out of the repository in
// keystore.properties at the project root. Absent, the release build still
// configures - so a checkout without the key can build debug - and only
// comes out unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// The ABIs the app ships for. PdfiumAndroid also carries x86; DjVuLibre is not
// built for it, so an x86 device handed that folder would open PDFs and crash
// on the first DjVu.
val abis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

// A release build comes out once per ABI as well as universal, for a GitHub
// release where sideloaders may want the smaller file. Debug stays one
// app-debug.apk, which is what the emulator workflow installs. AGP refuses
// ndk.abiFilters alongside ABI splits, hence the filter switching with it.
val splitByAbi = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

android {
    namespace = "com.rectobook.app"
    compileSdk = 35
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.rectobook.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        if (!splitByAbi) {
            ndk {
                abiFilters += abis
            }
        }

        externalNativeBuild {
            cmake {
                abiFilters += abis
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    splits {
        abi {
            isEnable = splitByAbi
            reset()
            include(*abis.toTypedArray())
            isUniversalApk = true
        }
    }

    // With the ndk filter switched off for splits, the universal APK would
    // pick up PdfiumAndroid's x86 libraries again - see abis above.
    packaging {
        jniLibs {
            excludes += "lib/x86/**"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        // The about screen shows the version, and reads it from here rather
        // than carrying a second copy that can disagree with the manifest.
        buildConfig = true
    }
}

// The release APKs under the names a GitHub download should carry:
// rectobook-0.1.apk for the universal one, rectobook-0.1-arm64-v8a.apk and so
// on per ABI. Copied rather than renamed in place - AGP's own output naming
// is a deprecated API, and debug keeps the app-debug.apk the emulator
// workflow installs.
val releaseApks = tasks.register<Sync>("releaseApks") {
    val version = android.defaultConfig.versionName
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("*.apk") }
    into(layout.buildDirectory.dir("outputs/github"))
    rename { name ->
        val abi = Regex("app-(.+)-release\\.apk").matchEntire(name)?.groupValues?.get(1)
        if (abi == null || abi == "universal") "rectobook-$version.apk" else "rectobook-$version-$abi.apk"
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(releaseApks) }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // PDFium (BSD/Apache), for PDF rendering and text. A prebuilt AAR rather
    // than a vendored source tree like DjVuLibre: PDFium builds through GN and
    // depot_tools, which is a project of its own.
    implementation("io.legere:pdfiumandroid:1.0.20")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
