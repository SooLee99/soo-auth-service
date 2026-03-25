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
class AdminAccountBootstrapRunner(
    private val properties: AdminBootstrapProperties,
    private val environment: Environment,
    private val adminAccountBootstrapService: AdminAccountBootstrapService,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val command = AdminBootstrapCommand.from(
            properties = properties,
            activeProfiles = environment.activeProfiles.toList(),
        )

        when (val result = adminAccountBootstrapService.bootstrap(command)) {
            is AdminBootstrapResult.Skipped -> {
                log.info("admin bootstrap skipped: {}", result.reason)
            }

            is AdminBootstrapResult.Applied -> {
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
        private val log = LoggerFactory.getLogger(AdminAccountBootstrapRunner::class.java)
    }
}