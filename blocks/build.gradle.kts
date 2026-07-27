// Lego Flow Blocks — Core DP/DF data processing framework
val slf4jVersion: String by project
val mockitoVersion: String by project

dependencies {
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
}
