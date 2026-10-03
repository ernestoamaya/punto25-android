plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val mapsApiKey = providers.gradleProperty("MAPS_API_KEY")
    .orElse(providers.environmentVariable("MAPS_API_KEY"))
    .orElse("NOT_CONFIGURED")
    .get()

val alphaKeystorePath = providers.environmentVariable("ALPHA_KEYSTORE_PATH").orNull?.takeIf { it.isNotBlank() }
val alphaKeystorePassword = providers.environmentVariable("ALPHA_KEYSTORE_PASSWORD").orNull?.takeIf { it.isNotBlank() }
val alphaKeyAlias = providers.environmentVariable("ALPHA_KEY_ALIAS").orNull?.takeIf { it.isNotBlank() }
val alphaKeyPassword = providers.environmentVariable("ALPHA_KEY_PASSWORD").orNull?.takeIf { it.isNotBlank() }
val hasPrivateAlphaSigning = listOf(alphaKeystorePath, alphaKeystorePassword, alphaKeyAlias, alphaKeyPassword).all { it != null }

android {
    namespace = "ar.com.mandados.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ar.com.mandados.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "0.3-alpha3-dev3.6"

        fun configValue(name: String): String =
            providers.gradleProperty(name).orNull
                ?: providers.environmentVariable(name).orNull
                ?: ""

        fun quoted(value: String): String =
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        buildConfigField("String", "FIREBASE_API_KEY", quoted(configValue("FIREBASE_API_KEY")))
        buildConfigField("String", "FIREBASE_APP_ID", quoted(configValue("FIREBASE_APP_ID")))
        buildConfigField("String", "FIREBASE_PROJECT_ID", quoted(configValue("FIREBASE_PROJECT_ID")))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", quoted(configValue("GOOGLE_WEB_CLIENT_ID")))
        buildConfigField("String", "PUNTO25_API_BASE_URL", quoted(configValue("PUNTO25_API_BASE_URL")))
        buildConfigField("String", "WHATSAPP_VERIFY_NUMBER", quoted(configValue("WHATSAPP_VERIFY_NUMBER")))
        buildConfigField("String", "ALPHA_ADMIN_PIN", quoted(configValue("ALPHA_ADMIN_PIN")))

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    signingConfigs {
        if (hasPrivateAlphaSigning) {
            create("privateAlpha") {
                storeFile = file(alphaKeystorePath!!)
                storePassword = alphaKeystorePassword
                keyAlias = alphaKeyAlias
                keyPassword = alphaKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfigs.findByName("privateAlpha")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.17.0")

    implementation("com.google.maps.android:maps-compose:8.3.0")
    implementation("com.google.android.gms:play-services-location:21.4.0")

    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
