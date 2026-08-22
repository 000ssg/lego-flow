// Lego Flow AMQP 0-9-1 — AMQP 0-9-1 protocol (RabbitMQ compatible)
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-service"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
