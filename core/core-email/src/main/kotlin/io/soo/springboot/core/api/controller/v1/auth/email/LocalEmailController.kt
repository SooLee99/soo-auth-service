package io.soo.springboot.core.api.controller.v1.auth.email

import io.soo.springboot.core.api.controller.v1.request.SignUpRequest
import io.soo.springboot.core.domain.authmethod.AuthMethodConfigService
import io.soo.springboot.core.domain.local.LocalAccountService
import io.soo.springboot.core.enums.AuthMethod
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/local/email")
@ConditionalOnProperty(name = ["app.auth.method.email.enabled"], havingValue = "true", matchIfMissing = true)
class LocalEmailController(
    private val authMethodConfigService: AuthMethodConfigService,
    private val localAccountService: LocalAccountService,
) {
    @PostMapping("/signup")
    fun signUp(@RequestBody @Valid request: SignUpRequest) {
        authMethodConfigService.assertEnabled(AuthMethod.EMAIL)
        localAccountService.signUpByEmail(
            email = request.email,
            password = request.password,
            phoneNumber = request.phoneNumber,
            phoneVerificationToken = request.phoneVerificationToken,
            gender = request.gender,
            locale = request.locale,
            nickname = request.nickname,
            name = request.name,
            profileImageUrl = request.profileImageUrl,
            thumbnailImageUrl = request.thumbnailImageUrl,
            birthyear = request.birthyear,
            birthday = request.birthday,
        )
    }
}
