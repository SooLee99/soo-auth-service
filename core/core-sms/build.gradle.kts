dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-auth-common"))
    implementation(project(":storage:db-core"))
    implementation(project(":clients:client-solapi"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
}
