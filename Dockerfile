# 1단계: 빌드 스테이지
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

ARG BUILDPLATFORM
ARG TARGETPLATFORM
ARG TARGETOS
ARG TARGETARCH

# Gradle 래퍼 및 설정 파일 복사
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts ./
COPY settings.gradle.kts ./
COPY gradle.properties ./

# Windows CRLF 줄바꿈 제거 + gradlew 실행 권한 부여
RUN sed -i 's/\r$//' ./gradlew && chmod +x ./gradlew

# 모든 모듈의 build.gradle.kts 파일 복사 (캐시 최적화)
COPY core/core-api/build.gradle.kts core/core-api/
COPY core/core-enum/build.gradle.kts core/core-enum/
COPY storage/db-core/build.gradle.kts storage/db-core/
COPY support/logging/build.gradle.kts support/logging/
COPY support/monitoring/build.gradle.kts support/monitoring/
COPY clients/client-solapi/build.gradle.kts clients/client-solapi/
COPY tests/api-docs/build.gradle.kts tests/api-docs/

# 의존성 캐시
RUN ./gradlew dependencies --no-daemon || true

# 전체 소스 복사 후 빌드
COPY . .

# 전체 소스 복사 이후 gradlew가 다시 덮일 수 있으므로 한 번 더 정리
RUN sed -i 's/\r$//' ./gradlew && chmod +x ./gradlew \
    && ./gradlew :core:core-api:bootJar --no-daemon -x test

# 2단계: 실행 스테이지
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /app/core/core-api/build/libs/*.jar ./app.jar

ENV SPRING_PROFILES_ACTIVE=live

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-live}"]
