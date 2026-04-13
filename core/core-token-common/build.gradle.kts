dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-support"))
    implementation(project(":storage:db-core"))

    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-web")
}
