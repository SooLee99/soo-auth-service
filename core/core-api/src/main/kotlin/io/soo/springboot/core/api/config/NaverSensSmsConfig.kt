package io.soo.springboot.core.api.config

import io.soo.springboot.core.domain.NaverSensSmsProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(NaverSensSmsProperties::class)
class NaverSensSmsConfig
