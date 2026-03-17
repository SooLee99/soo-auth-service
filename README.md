# SpringBoot kotlin Template
### [기존 템플릿 출처]
[![Twitter](https://img.shields.io/twitter/url?style=social&url=https%3A%2F%2Ftwitter.com%2Fgeminikims)](https://twitter.com/geminikims)
[![Youtube](https://img.shields.io/youtube/channel/views/UCDh8zEDofOcrOMAOnSVL9Tg?label=Youtube&style=social)](https://www.youtube.com/@geminikims)
[![CI](https://github.com/team-dodn/spring-boot-java-template/actions/workflows/ci.yml/badge.svg)](https://github.com/team-dodn/spring-boot-java-template/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://opensource.org/licenses/Apache-2.0)

이 구조는 최적의 구조는 아닙니다. 그러나 프로젝트 초기 단계에서 생산성이 중요한 경우에 유용한 기본 구조입니다.

소프트웨어가 성장함에 따라, 구조 또한 확장되어야 함을 기억하세요.

## 개발 가이드

- API 개발 규칙/동작 과정: [docs/README_API_RULES.md](docs/README_API_RULES.md)
- 로컬 로그 모니터링: [docs/README_LOG_MONITORING.md](docs/README_LOG_MONITORING.md)

# **모듈**

## Core
이 모듈의 각 하위 모듈은 하나의 도메인 서비스를 담당합니다.

서비스의 성장에 맞춰 모듈 구조도 함께 확장해야 합니다.

### core:core-api
이 모듈은 프로젝트에서 유일한 실행 가능한 모듈입니다. 초기 개발 생산성을 극대화할 수 있도록 도메인 구조가 설정되어 있습니다.

이 모듈은 또한 API를 제공하고 서비스의 프레임워크 설정을 담당합니다.

### core:core-enum
이 모듈은 `core-api`에서 사용되며 외부 모듈로 전달해야 하는 열거형을 포함하고 있습니다.

<br/>

## Clients
이 모듈의 하위 모듈은 외부 시스템과의 통합을 담당합니다.

### clients:clients-example
이 모듈은 `Spring-Cloud-Open-Feign`을 사용한 HTTP 통신 예시를 제공합니다.

<br/>

## Storage
이 모듈의 하위 모듈은 다양한 저장소와의 통합을 담당합니다.

### storage:db-core
이 모듈은 `Spring-Data-JPA`를 사용하여 `MySql`에 연결하는 예시를 제공합니다.

<br/>

## Support
이 모듈의 하위 모듈은 추가적인 지원을 담당합니다.

### support:logging
이 모듈은 서비스의 로깅을 지원하며, 분산 추적을 위한 종속성을 추가로 제공합니다.

또한 `Sentry`를 지원하는 종속성도 포함하고 있습니다.

### support:monitoring
이 모듈은 서비스 모니터링을 지원합니다.

<br/>

## Tests
이 모듈의 하위 모듈은 테스트 코드를 작성하는 데 편리함을 제공합니다.

### tests:api-docs
이 모듈은 spring-rest-docs를 편리하게 작성할 수 있도록 지원합니다.

<br/>

# 종속성 관리
모든 종속성 버전 관리는 `gradle.properties` 파일을 통해 수행됩니다.

새로운 종속성을 추가하려면 `gradle.properties`에 버전을 추가하고, 이를 `build.gradle`에서 로드하면 됩니다.

<br/>

# 실행 프로필

## local
이 프로필은 네트워크 연결이 끊어져도 개발할 수 있는 환경을 구성하는 데 사용됩니다.

## local-dev
이 프로필은 로컬 머신에서 DEV 환경에 연결할 수 있는 설정을 제공합니다.

## dev
이 프로필은 개발 환경을 배포하는 데 사용됩니다.

## staging
이 프로필은 스테이징 환경을 배포하는 데 사용됩니다.

## live
이 프로필은 라이브 환경을 배포하는 데 사용됩니다.

<br/>

# 테스트 작업 및 태그

## test
이 작업은 `CI`에서 실행하고 싶은 테스트 작업들의 모음입니다.

설정을 변경하려면 `build.gradle` 파일을 수정하십시오.

## unitTest
이 작업은 일반적으로 의존성이 없고, 빠르게 실행되며 단일 기능을 테스트하는 테스트들입니다.

## contextTest
이 작업은 SpringContext와 함께 실행되며, 통합 테스트를 수행합니다.

## restDocsTest
이 작업은 spring-rest-docs를 기반으로 asciidoc을 생성하는 작업입니다.

## developTest
이 작업은 `CI`에서 실행되지 않아야 하는 테스트 작업입니다.

테스트 작성에 익숙하지 않다면 이 태그를 사용하는 것이 좋습니다.

<br/>

# 권장 설정

## Git Hook
이 설정은 커밋 시마다 `lint`를 실행하도록 설정합니다.

```
$ git config core.hookspath .githooks
```

## IntelliJ IDEA
이 설정은 `test code`를 바로 실행할 수 있도록 설정합니다.

```
// IntelliJ IDEA에서 Gradle 빌드 및 실행
Build, Execution, Deployment > Build Tools > Gradle > Run tests using > IntelliJ IDEA	
```

IntelliJ IDEA의 포맷에 lint 설정을 적용하려면 아래 가이드를 참조하십시오.

[Spring Java Format IntelliJ IDEA](https://github.com/spring-io/spring-javaformat#intellij-idea)

---

# 지원
<div align="center"><a href="https://jb.gg/OpenSourceSupport"><img src="https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.png" alt="JetBrains 로고" width="240"></a></div>
