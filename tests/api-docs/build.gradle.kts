dependencies {
    implementation("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework.boot:spring-boot-starter-test")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    api("org.springframework.restdocs:spring-restdocs-restassured")
    api("org.springframework.restdocs:spring-restdocs-mockmvc")
    api("com.epages:restdocs-api-spec-mockmvc:0.19.4")
    api("io.rest-assured:spring-mock-mvc")
}