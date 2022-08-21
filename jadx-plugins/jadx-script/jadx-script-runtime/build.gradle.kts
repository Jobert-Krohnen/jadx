plugins {
	id("jadx-library")

    kotlin("jvm") version "1.7.10"
}

dependencies {
	api(project(":jadx-core"))

	implementation("org.jetbrains.kotlin:kotlin-scripting-common")
	implementation("org.jetbrains.kotlin:kotlin-scripting-jvm")

	// allow to use maven dependencies in scripts
	implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies")
	implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies-maven")

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")
	implementation("io.github.microutils:kotlin-logging-jvm:2.1.20")
}
