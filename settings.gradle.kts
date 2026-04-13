rootProject.name = "soo-auth-service"

include(
    "core:core-enum",
    "core:core-support",
    "core:core-auth-common",
    "core:core-token-common",
    "core:core-user-common",
    "core:core-login-common",
    "core:core-phone-common",
    "core:core-admin",
    "core:core-email",
    "core:core-id",
    "core:core-sms",
    "core:core-oauth2",
    "core:core-api",
    "storage:db-core",
    "tests:api-docs",
    "support:logging",
    "support:monitoring",
    "clients:client-solapi"
)

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    val kotlinVersion: String by settings
    val springBootVersion: String by settings
    val springDependencyManagementVersion: String by settings
    val asciidoctorConvertVersion: String by settings
    val ktlintVersion: String by settings
    val restdocsApiSpecVersion: String by settings

    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.jetbrains.kotlin.jvm" -> useVersion(kotlinVersion)
                "org.jetbrains.kotlin.kapt" -> useVersion(kotlinVersion)
                "org.jetbrains.kotlin.plugin.spring" -> useVersion(kotlinVersion)
                "org.jetbrains.kotlin.plugin.jpa" -> useVersion(kotlinVersion)
                "org.springframework.boot" -> useVersion(springBootVersion)
                "io.spring.dependency-management" -> useVersion(springDependencyManagementVersion)
                "org.asciidoctor.jvm.convert" -> useVersion(asciidoctorConvertVersion)
                "org.jlleitschuh.gradle.ktlint" -> useVersion(ktlintVersion)
                "com.epages.restdocs-api-spec" -> useVersion(restdocsApiSpecVersion)
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
