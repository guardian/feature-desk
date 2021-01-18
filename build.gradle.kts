plugins {
    kotlin("jvm") version "1.3.72"
}

group = "com.theguardian"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.eclipse.jgit:org.eclipse.jgit-parent:5.5.1.201910021850-r")
}
