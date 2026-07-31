plugins {
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jacoco:org.jacoco.core:0.8.14")
    implementation("org.jacoco:org.jacoco.report:0.8.14")
}
