    plugins {
        alias(libs.plugins.android.application)

        id("com.google.gms.google-services")
    }

    android {
        namespace = "com.smart_nootes.udhaarapp"
        compileSdk = 35

        defaultConfig {
            applicationId = "com.smart_nootes.udhaarapp"
            minSdk = 24
            targetSdk = 34
            versionCode = 1
            versionName = "1.0"

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }

    dependencies {

        implementation(libs.appcompat)
        implementation(libs.material)
        implementation(libs.activity)
        implementation(libs.constraintlayout)
        testImplementation(libs.junit)
        androidTestImplementation(libs.ext.junit)
        androidTestImplementation(libs.espresso.core)

        // Firebase BoM
        implementation(platform("com.google.firebase:firebase-bom:35.0.1"))

    // Firebase Analytics
        implementation("com.google.firebase:firebase-analytics")

    // Firebase Authentication
        implementation("com.google.firebase:firebase-auth")

    // Firebase Cloud Firestore
        implementation("com.google.firebase:firebase-firestore")

        // WorkManager for automatic background sync
        implementation("androidx.work:work-runtime:2.10.1")
        // Add the dependencies for any other desired Firebase products
        // https://firebase.google.com/docs/android/setup#available-libraries
    }