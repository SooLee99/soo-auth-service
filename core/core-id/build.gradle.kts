dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-support"))
    implementation(project(":core:core-auth-common"))
    implementation(project(":core:core-user-common"))
    implementation(project(":core:core-login-common"))
    implementation(project(":core:core-phone-common"))
    implementation(project(":storage:db-core"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
}
