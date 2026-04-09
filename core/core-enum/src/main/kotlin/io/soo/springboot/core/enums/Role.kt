package io.soo.springboot.core.enums

enum class Role(val authority: String) {
    USER("ROLE_USER"),
    ADMIN("ROLE_ADMIN"),
}
