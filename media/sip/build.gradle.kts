// Lego Flow SIP — Session Initiation Protocol (RFC 3261)
val slf4jVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    api(project(":media:common"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
