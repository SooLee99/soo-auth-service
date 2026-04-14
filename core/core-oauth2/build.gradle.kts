dependencies {
    implementation(project(":core:core-api"))
    implementation(project(":core:core-enum"))
    implementation(project(":storage:db-core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework:spring-tx")
}
