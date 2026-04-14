package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.SignUpRequest
import io.soo.springboot.core.domain.auth.AuthFeature
import io.soo.springboot.core.domain.auth.AuthFeatureGuard
import io.soo.springboot.core.domain.local.LocalAccountService
import io.soo.springboot.core.domain.local.LocalSignUpCommand
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/local")
class EmailAuthController(
    private val authFeatureGuard: AuthFeatureGuard,
    private val localAccountService: LocalAccountService,
) {
    @PostMapping("/email/signup")
    fun signUpWithEmail(@RequestBody @Valid request: SignUpRequest) {
        authFeatureGuard.assertEnabled(AuthFeature.EMAIL)
        localAccountService.signUp(
            LocalSignUpCommand(
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
            ),
        )
    }

    @PostMapping("/signup")
    fun signUp(@RequestBody @Valid request: SignUpRequest) {
        signUpWithEmail(request)
    }
}
