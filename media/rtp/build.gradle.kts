// Lego Flow RTP — Real-time Transport Protocol (RFC 3550)
val slf4jVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    api(project(":lego-flow-media-common"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
