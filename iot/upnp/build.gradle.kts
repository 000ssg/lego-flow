// Lego Flow UPnP — Universal Plug and Play / DLNA
val slf4jVersion: String by project
val mockitoVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    api(project(":web:http"))
    api(project(":web:web-services"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    implementation("com.googlecode.soundlibs:tritonus-share:0.3.7.4")
    implementation("org.jflac:jflac-codec:1.5.2")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
}
