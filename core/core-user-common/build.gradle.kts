dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-support"))
    implementation(project(":core:core-token-common"))
    implementation(project(":storage:db-core"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
}
