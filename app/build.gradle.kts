plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val gdxVersion = "1.13.5"
val natives by configurations.creating

android {
    namespace = "com.dota3.moba"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dota3.moba"
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "4.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        named("main") {
            jniLibs.srcDirs("libs")
        }
    }

    signingConfigs {
        create("releaseAuto") {
            val keystoreFile = rootProject.file("keystore/release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "dota3release"
                keyAlias = System.getenv("KEY_ALIAS") ?: "dota3key"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "dota3release"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystoreFile = rootProject.file("keystore/release.keystore")
            signingConfig = if (keystoreFile.exists()) {
                signingConfigs.getByName("releaseAuto")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")

    testImplementation("junit:junit:4.13.2")
}

val copyAndroidNatives by tasks.registering {
    doLast {
        val libsDir = project.file("libs")
        libsDir.mkdirs()
        natives.files.forEach { jar ->
            val abi = when {
                jar.name.endsWith("natives-armeabi-v7a.jar") -> "armeabi-v7a"
                jar.name.endsWith("natives-arm64-v8a.jar") -> "arm64-v8a"
                jar.name.endsWith("natives-x86_64.jar") -> "x86_64"
                jar.name.endsWith("natives-x86.jar") -> "x86"
                else -> null
            }
            if (abi != null) {
                val outDir = project.file("libs/$abi")
                outDir.mkdirs()
                project.copy {
                    from(project.zipTree(jar))
                    into(outDir)
                    include("*.so")
                }
            }
        }
    }
}

tasks.configureEach {
    if (name.contains("preBuild") || name.contains("mergeDebugJniLibFolders") || name.contains("mergeReleaseJniLibFolders")) {
        dependsOn(copyAndroidNatives)
    }
}
