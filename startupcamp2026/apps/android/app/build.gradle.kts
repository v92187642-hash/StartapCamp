plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ru.otmetka.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.otmetka.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("apiBaseUrl").get()}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The API client is generated into build/, never committed and never edited.
    sourceSets["main"].java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
}

kotlin {
    jvmToolchain(17)
}

// Generates the API client from the shared contract. Request code is never
// written by hand: change api/openapi.yaml and rebuild.
openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-retrofit2")
    inputSpec.set(rootDir.resolve("../../api/openapi.yaml").path)
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    packageName.set("ru.otmetka.android.api")
    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "useCoroutines" to "true",
            "dateLibrary" to "java8",
        )
    )
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
}

// Everything that reads Kotlin sources (the compiler, and KSP for Room) needs the generated client first.
tasks.matching { it.name.startsWith("ksp") || it.name.startsWith("compile") && it.name.endsWith("Kotlin") }
    .configureEach { dependsOn(tasks.named("openApiGenerate")) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.coroutines.android)

    // Local copy of plans and marks, so the app works offline.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    // Sends marks made offline once the network is back.
    implementation(libs.work.runtime)

    // Used by the generated API client.
    implementation(libs.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.serialization)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
}
