dependencies {
    implementation(project(":core:core-admin"))
    implementation(project(":core:core-email"))
    implementation(project(":core:core-id"))
    implementation(project(":core:core-sms"))
    implementation(project(":core:core-oauth2"))
    implementation(project(":storage:db-core"))
    implementation(project(":support:logging"))
    implementation(project(":support:monitoring"))

    // 컨텍스트 테스트 전용: 아래 타입들은 core-oauth2 등의 implementation 의존이라
    // core-api 컴파일 클래스패스로 전이되지 않으므로 테스트 클래스패스에 직접 노출한다.
    testImplementation(project(":clients:client-kakao")) // KakaoOAuthClient(@Primary mockk 대상)
    testImplementation(project(":core:core-support")) // ErrorType
    testImplementation(project(":core:core-phone-common")) // PhoneVerificationNotifier(@Primary mockk 대상)
    testImplementation(project(":core:core-enum")) // UserStatus(탈퇴 soft-delete 영속 검증)
    testImplementation("org.springframework.boot:spring-boot-starter-web") // MediaType 등 spring-web
}

tasks.getByName("bootJar") {
    enabled = true
}

tasks.getByName("jar") {
    enabled = false
}
