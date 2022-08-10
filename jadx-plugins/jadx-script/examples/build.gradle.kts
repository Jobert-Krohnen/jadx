plugins {
    kotlin("jvm") version "1.7.10"
}

dependencies {
	implementation(project(":jadx-plugins:jadx-script:jadx-script-runtime"))

	implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
	implementation("org.jetbrains.kotlin:kotlin-script-runtime")

	implementation("io.github.microutils:kotlin-logging-jvm:2.1.20")

	// for deobf2 script
	implementation("com.github.javafaker:javafaker:1.0.2")
}

sourceSets {
	main {
		java.srcDirs("scripts", "context")
	}
}