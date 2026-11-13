plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    `maven-publish`
}

group = "com.github.Mironlive"
version = "0.1.0"

android {
    namespace = "io.github.mironlive.swipeselect"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(bom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.Mironlive"
            artifactId = "swipe-select-compose"
            version = project.version.toString()
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("SwipeSelect for Jetpack Compose")
                description.set("Swipe to multi-select for Compose lazy lists and grids.")
                url.set("https://github.com/Mironlive/swipe-select-compose")
                licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
            }
        }
    }
}
