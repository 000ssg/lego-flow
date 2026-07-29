// Lego Flow STOMP — Simple Text Oriented Messaging Protocol
val slf4jVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    api(project(":lego-flow-http"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
