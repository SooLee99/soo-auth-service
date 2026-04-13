package io.soo.springboot.core.domain.bootstrap

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "app.bootstrap.admin",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class AdminInitRunner(
    private val properties: AdminInitProperties,
    private val environment: Environment,
    private val adminInitService: AdminInitService,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val command = AdminInitCommand.of(
            properties = properties,
            activeProfiles = environment.activeProfiles.toList(),
        )

        when (val result = adminInitService.bootstrap(command)) {
            is AdminInitResult.Skipped -> {
                log.info("admin bootstrap skipped: {}", result.reason)
            }

            is AdminInitResult.Applied -> {
                log.info(
                    "admin bootstrap applied: username={}, accountCreated={}, rolePromoted={}, credentialCreated={}",
                    command.username,
                    result.accountCreated,
                    result.rolePromoted,
                    result.credentialCreated,
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AdminInitRunner::class.java)
    }
}
