import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.gradle.jvm.tasks.Jar

val emailModuleEnabled = (findProperty("auth.module.email.enabled") as String?)?.toBoolean() ?: true
val phoneModuleEnabled = (findProperty("auth.module.phone.enabled") as String?)?.toBoolean() ?: true
val idModuleEnabled = (findProperty("auth.module.id.enabled") as String?)?.toBoolean() ?: true
val oauth2ModuleEnabled = (findProperty("auth.module.oauth2.enabled") as String?)?.toBoolean() ?: true

dependencies {
    implementation(project(":core:core-api"))
    if (emailModuleEnabled) {
        implementation(project(":core:core-email"))
    }
    if (phoneModuleEnabled) {
        implementation(project(":core:core-phone"))
    }
    if (idModuleEnabled) {
        implementation(project(":core:core-id"))
    }
    if (oauth2ModuleEnabled) {
        implementation(project(":core:core-oauth2"))
    }
}

tasks.named<BootJar>("bootJar") {
    enabled = true
}

tasks.named<Jar>("jar") {
    enabled = false
}
