# 1단계: 빌드 스테이지
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Gradle 래퍼 및 설정 파일 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# 모든 모듈의 build.gradle.kts 파일들을 복사 (캐싱을 위해)
COPY core/core-api/build.gradle.kts core/core-api/
COPY core/core-enum/build.gradle.kts core/core-enum/
COPY storage/db-core/build.gradle.kts storage/db-core/
COPY support/logging/build.gradle.kts support/logging/
COPY support/monitoring/build.gradle.kts support/monitoring/
COPY clients/client-example/build.gradle.kts clients/client-example/
COPY tests/api-docs/build.gradle.kts tests/api-docs/

# 의존성 다운로드 (캐싱을 위해)
RUN ./gradlew dependencies --no-daemon || true

# 소스 코드 복사 및 빌드
COPY . .
RUN ./gradlew :core:core-api:bootJar --no-daemon -x test

# 2단계: 실행 스테이지
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 빌드 스테이지에서 생성된 jar 파일 복사
COPY --from=build /app/core/core-api/build/libs/*.jar app.jar

# 환경 변수 설정
ENV SPRING_PROFILES_ACTIVE=dev

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
