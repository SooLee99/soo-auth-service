package io.soo.springboot.core.domain.oauth2

import io.soo.springboot.core.enums.Gender

abstract class OAuth2ParserSupport {
    protected fun Map<String, Any?>.str(key: String): String? =
        this[key]?.toString()?.takeIf { it.isNotBlank() }

    protected fun Map<String, Any?>.anyStr(vararg keys: String): String? =
        keys.asSequence().mapNotNull { str(it) }.firstOrNull()

    @Suppress("UNCHECKED_CAST")
    protected fun Map<String, Any?>.map(key: String): Map<String, Any?>? =
        (this[key] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }

    protected fun Map<String, Any?>.bool(key: String): Boolean? =
        when (val v = this[key]) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true) || v == "Y"
            is Number -> v.toInt() != 0
            else -> null
        }

    protected fun String.toBirthdayMMddOrMMdd(): String? {
        val s = this.trim()
        if (Regex("""^\d{2}-\d{2}$""").matches(s)) return s
        if (Regex("""^\d{4}$""").matches(s)) return "${s.substring(0, 2)}-${s.substring(2, 4)}"
        return null
    }

    protected fun String.toGenderKakao(): Gender =
        when (this.lowercase()) {
            "male" -> Gender.MALE
            "female" -> Gender.FEMALE
            else -> Gender.UNKNOWN
        }

    protected fun String.toGenderNaver(): Gender =
        when (this.uppercase()) {
            "M" -> Gender.MALE
            "F" -> Gender.FEMALE
            else -> Gender.UNKNOWN
        }
}

