dependencies {
    implementation(project(":core:core-admin"))
    implementation(project(":core:core-email"))
    implementation(project(":core:core-id"))
    implementation(project(":core:core-sms"))
    implementation(project(":core:core-oauth2"))
    implementation(project(":storage:db-core"))
    implementation(project(":support:logging"))
    implementation(project(":support:monitoring"))
}

tasks.getByName("bootJar") {
    enabled = true
}

tasks.getByName("jar") {
    enabled = false
}
