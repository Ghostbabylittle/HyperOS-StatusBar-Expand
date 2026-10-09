plugins {
    id("com.android.application")
}

android {
    namespace = "com.hyperos.statusbar.expand"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hyperos.statusbar.expand"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    compileOnly("de.robv.android.xposed:api:82")
}
