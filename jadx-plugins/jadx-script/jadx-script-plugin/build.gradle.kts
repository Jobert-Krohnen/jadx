plugins {
	id("jadx-library")

    kotlin("jvm") version "1.7.10"
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-scripting-common")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host")

	implementation(project(":jadx-plugins:jadx-script:jadx-script-runtime"))

	implementation("io.github.microutils:kotlin-logging-jvm:2.1.20")
}