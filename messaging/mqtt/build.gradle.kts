// Lego Flow MQTT — IoT Pub/Sub Messaging Protocol
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-blocks"))
    api(project(":lego-flow-service"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation(project(":lego-flow-acl"))
}
