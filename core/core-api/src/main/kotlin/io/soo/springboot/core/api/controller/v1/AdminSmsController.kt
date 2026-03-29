package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.AdminSmsSendReq
import io.soo.springboot.core.api.controller.v1.response.AdminSmsLogRes
import io.soo.springboot.core.api.controller.v1.response.AdminSmsSendRes
import io.soo.springboot.core.api.controller.v1.response.AdminSmsStatRes
import io.soo.springboot.core.domain.SmsAdmin
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/auth/admin/sms")
class AdminSmsController(
    private val smsAdmin: SmsAdmin,
) {

    @PostMapping("/send", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun sendSms(
        @RequestBody @Valid body: AdminSmsSendReq,
        req: HttpServletRequest,
    ): ApiResponse<AdminSmsSendRes> {
        val result = smsAdmin.send(body.to, body.text)
        return ApiResponse.success(req = req, data = AdminSmsSendRes.from(result))
    }

    @GetMapping("/logs", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listSmsLogs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime?,
        req: HttpServletRequest,
    ): ApiResponse<Page<AdminSmsLogRes>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = smsAdmin.logs(pageable, startDate, endDate).map { AdminSmsLogRes.from(it) }
        return ApiResponse.success(req = req, data = result)
    }

    @GetMapping("/stats", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getSmsStats(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime?,
        req: HttpServletRequest,
    ): ApiResponse<AdminSmsStatRes> {
        val result = smsAdmin.stat(startDate, endDate)
        return ApiResponse.success(req = req, data = AdminSmsStatRes.from(result))
    }
}
