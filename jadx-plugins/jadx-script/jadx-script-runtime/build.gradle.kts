plugins {
	id("jadx-library")

    kotlin("jvm") version "1.7.10"
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-scripting-common")
	implementation("org.jetbrains.kotlin:kotlin-scripting-jvm")

	// allow to use maven dependencies in scripts
	implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies")
	implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies-maven")

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")
	implementation("io.github.microutils:kotlin-logging-jvm:2.1.20")

	api(project(":jadx-plugins:jadx-plugins-api"))
	api(project(":jadx-core")) // TODO: workaround

	runtimeOnly(project(":jadx-plugins:jadx-dex-input"))
	runtimeOnly(project(":jadx-plugins:jadx-smali-input"))
	runtimeOnly(project(":jadx-plugins:jadx-java-convert"))
	runtimeOnly(project(":jadx-plugins:jadx-java-input"))
	runtimeOnly(project(":jadx-plugins:jadx-raung-input"))

}