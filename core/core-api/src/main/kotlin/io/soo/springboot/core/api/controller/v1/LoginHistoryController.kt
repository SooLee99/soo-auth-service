package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.security.auth.UserIdResolver
import io.soo.springboot.core.support.response.ApiResponse
import io.soo.springboot.storage.db.core.LoginHistory
import io.soo.springboot.storage.db.core.LoginHistoryRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/auth/login-history")
class LoginHistoryController(
    private val loginHistoryRepository: LoginHistoryRepository,
    private val userIdResolver: UserIdResolver,
) {

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getLoginHistory(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime?,
        req: HttpServletRequest,
    ): ApiResponse<Page<LoginHistory>> {
        val userId = userIdResolver.resolve(authentication)
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))

        val result = if (startDate != null && endDate != null) {
            loginHistoryRepository.findByUserIdAndDateRange(userId, startDate, endDate, pageable)
        } else {
            loginHistoryRepository.findByUserId(userId, pageable)
        }

        return ApiResponse.success(req = req, data = result)
    }
}
