dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-auth-common"))
    implementation(project(":core:core-sms"))
    implementation(project(":storage:db-core"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
}
