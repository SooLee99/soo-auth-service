package io.soo.springboot.core.domain

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.health.links")
data class HealthLinksProperties(
    val docs: Docs = Docs(),
    val monitoring: Monitoring = Monitoring(),
    val logs: Logs = Logs(),
) {
    data class Docs(
        val swagger: String = "{baseUrl}/docs/swagger/index.html",
        val docs: String = "{baseUrl}/docs/index.html",
    )

    data class Monitoring(
        val grafana: String = "{scheme}://{host}:3000",
        val prometheus: String = "{baseUrl}/actuator/prometheus",
        val loki: String = "{scheme}://{host}:3100",
    )

    data class Logs(
        val lokiQuery: String = "{scheme}://{host}:3100/loki/api/v1/query_range",
        val grafanaExplore: String = "{scheme}://{host}:3000/explore",
    )
}
