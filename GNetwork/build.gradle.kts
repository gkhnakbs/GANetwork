import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.java.library)
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

group = "com.gkhnakbs"
version = "1.0.0"

base {
    archivesName.set("gnetwork")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            artifactId = "gnetwork"

            // İstersen groupId ve version'u da buraya sabitleyebilirsin
            groupId = "com.gkhnakbs"
            version = "1.0.0"
        }
    }
}