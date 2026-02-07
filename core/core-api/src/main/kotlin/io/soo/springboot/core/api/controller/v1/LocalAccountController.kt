package io.soo.springboot.core.api.controller.v1

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

import io.soo.springboot.core.api.controller.v1.request.SignUpRequest
import io.soo.springboot.core.domain.LocalAccountService


@Controller
@RequestMapping("/api/v1/auth")
class LocalAccountController(
    private val localAccountService: LocalAccountService
) {
    @PostMapping("/signup")
    fun signUp(@RequestBody @Valid request: SignUpRequest) {
        localAccountService.signUp(
            request.email,
            request.password,
            request.nickname,
            request.name,
            request.locale,
            request.gender,
            request.phoneNumber,
            request.profileImageUrl,
            request.thumbnailImageUrl,
            request.birthyear,
            request.birthday,
        )
    }
}
