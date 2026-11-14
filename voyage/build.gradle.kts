plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.riad.voyage"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.riad.voyage"
        minSdk = 26
        targetSdk = 35
        // Numéro de version croissant à chaque compilation GitHub, pour que les mises à jour soient acceptées.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
    }

    // Signature de la version publiée : la clé privée n'est jamais dans le dépôt. La CI la reçoit
    // des secrets GitHub (variables ENVOLEE_*). Sans elles, la version release n'est pas signée.
    signingConfigs {
        // Clé fixe (versionnée) : chaque nouvelle version s'installe par-dessus l'ancienne.
        // Clé de test publique : à remplacer par la clé privée (secrets ENVOLEE_*) avant une vraie publication.
        getByName("debug") {
            storeFile = file("debug-signing.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val path = System.getenv("ENVOLEE_KEYSTORE_FILE")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("ENVOLEE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ENVOLEE_KEY_ALIAS")
                keyPassword = System.getenv("ENVOLEE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            if (System.getenv("ENVOLEE_KEYSTORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
            // R8 réduit les bibliothèques ; le code de l'app est conservé tel quel (proguard-rules.pro)
            // pour éviter toute casse à l'exécution (lecture JSON, WorkManager…).
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    lint {
        // Les vérifications de lint ne bloquent pas la publication (elles restent visibles dans les rapports).
        abortOnError = false
        checkReleaseBuilds = false
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Carte OpenStreetMap (sans clé d'API) pour l'itinéraire du programme.
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // org.json fait partie d'Android, mais pas de la JVM utilisée par les tests unitaires.
    testImplementation("org.json:json:20240303")
}
