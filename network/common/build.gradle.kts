// Lego Flow Network Common — BER/ASN.1 Codec
val slf4jVersion: String by project

dependencies {
    api(project(":blocks"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
