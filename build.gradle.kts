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
    implementation("org.eclipse.jgit","org.eclipse.jgit", "5.10.0.202012080955-r")
    implementation("org.slf4j", "slf4j-log4j12", "1.7.30")
}
