import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.isFile) {
    localPropertiesFile.inputStream().use { stream ->
        localProperties.load(stream)
    }
}

android {
    namespace = "com.mirandadevsource.exquisssitaappmanager"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mirandadevsource.exquisssitaappmanager"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        fun buildConfigValue(property: String): String = localProperties.getProperty(property)
            .orEmpty()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val supabaseKey = buildConfigValue("supabase.publishable.key")
            .ifBlank { buildConfigValue("supabase.anon.key") }
        require(!supabaseKey.startsWith("sb_secret_", ignoreCase = true)) {
            "La clave de Supabase configurada es secreta. Usa únicamente una clave publishable/anon en el APK."
        }
        if (supabaseKey.count { it == '.' } == 2) {
            val payload = runCatching {
                val encodedPayload = supabaseKey.split('.')[1]
                String(Base64.getUrlDecoder().decode(encodedPayload), Charsets.UTF_8)
            }.getOrNull()
            require(payload?.contains(Regex("\\\"role\\\"\\s*:\\s*\\\"anon\\\"")) == true) {
                "El JWT de Supabase configurado no declara role=anon; no se permiten service_role ni JWT desconocidos en el APK."
            }
        }
        buildConfigField("String", "SUPABASE_URL", "\"${buildConfigValue("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabaseKey\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.supabase.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)
    implementation(libs.hilt.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    coreLibraryDesugaring(libs.androidx.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
