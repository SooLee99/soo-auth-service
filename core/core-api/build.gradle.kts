plugins {
    id("com.epages.restdocs-api-spec")
}

tasks.getByName("bootJar") { enabled = true }
tasks.getByName("jar") { enabled = false }

dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":support:monitoring"))
    implementation(project(":support:logging"))
    implementation(project(":storage:db-core"))
    implementation(project(":clients:client-example"))
    implementation(project(":clients:client-solapi"))
    testImplementation(project(":tests:api-docs"))

    testImplementation("com.epages:restdocs-api-spec-mockmvc")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springDocVersion")}")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    implementation("org.springframework.session:spring-session-core")
    implementation("org.springframework.session:spring-session-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}

openapi3 {
    format = "yaml"
    title = "soo-auth-service API"
    description = "인증 서비스 API 가이드 (REST API Docs)."
    version = project.version.toString()
    tagDescriptionsPropertiesFile = "src/test/resources/tags.yaml"
    snippetsDirectory = layout.buildDirectory.dir("generated-snippets").get().asFile.absolutePath
    outputDirectory = layout.buildDirectory.dir("api-spec").get().asFile.absolutePath
}

tasks.named<Test>("restDocsTest") {
    val snippetsDir = layout.buildDirectory.dir("generated-snippets").get().asFile
    systemProperty("org.springframework.restdocs.outputDir", snippetsDir.absolutePath)
    outputs.dir(snippetsDir)
}

gradle.projectsEvaluated {
    tasks.findByName("openapi3")?.setDependsOn(listOf("restDocsTest"))
}

val copyOpenApi3ToStatic by tasks.registering(Copy::class) {
    dependsOn("openapi3")
    from(layout.buildDirectory.file("api-spec/openapi3.yaml"))
    into(layout.projectDirectory.dir("src/main/resources/static/docs"))
}

tasks.register("generateApiDocs") {
    dependsOn("openapi3")
    dependsOn(copyOpenApi3ToStatic)
}
