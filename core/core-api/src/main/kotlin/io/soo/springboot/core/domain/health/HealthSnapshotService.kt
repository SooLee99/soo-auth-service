package io.soo.springboot.core.domain.health

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.info.GitProperties
import org.springframework.core.env.Environment
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import com.zaxxer.hikari.HikariDataSource
import jakarta.servlet.http.HttpServletRequest
import java.lang.management.ManagementFactory
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.FileStore
import java.nio.file.Path
import java.time.Instant
import javax.sql.DataSource

@Service
class HealthSnapshotService(
    private val environment: Environment,
    private val dataSourceProvider: ObjectProvider<DataSource>,
    private val redisTemplateProvider: ObjectProvider<StringRedisTemplate>,
    private val buildPropertiesProvider: ObjectProvider<BuildProperties>,
    private val gitPropertiesProvider: ObjectProvider<GitProperties>,
    private val healthLinksProperties: HealthLinksProperties,
) {
    fun publicSummary(req: HttpServletRequest): Map<String, Any> {
        val overall = evaluateOverallStatus(req = req, includeSystem = false, includeMonitoring = false)
        return linkedMapOf(
            "status" to overall.overallStatus,
            "application" to appName(),
            "version" to appVersion(),
            "startedAt" to startedAtIso(),
            "uptimeSec" to uptimeSeconds(),
            "links" to publicLinks(req),
        )
    }

    fun adminDetails(req: HttpServletRequest): Map<String, Any> {
        val overall = evaluateOverallStatus(req = req, includeSystem = true, includeMonitoring = true)
        return linkedMapOf(
            "status" to overall.overallStatus,
            "application" to appName(),
            "version" to appVersion(),
            "startedAt" to startedAtIso(),
            "profiles" to environment.activeProfiles.toList(),
            "uptimeSec" to uptimeSeconds(),
            "build" to buildInfo(),
            "components" to overall.components,
            "system" to overall.system,
            "links" to adminLinks(req),
        )
    }

    private fun evaluateOverallStatus(
        req: HttpServletRequest,
        includeSystem: Boolean,
        includeMonitoring: Boolean,
    ): HealthAggregate {
        val components = linkedMapOf<String, Map<String, Any?>>()
        val db = checkDatabase()
        components["database"] = db
        val redis = checkRedis()
        components["redis"] = redis
        if (includeMonitoring) {
            components["monitoring"] = checkMonitoring(req)
        }

        val statuses = listOfNotNull(
            db["status"] as? String,
            redis["status"] as? String,
            (components["monitoring"]?.get("status") as? String),
        )
        val overall = when {
            statuses.any { it == "DOWN" } -> "DOWN"
            statuses.any { it == "DEGRADED" } -> "DEGRADED"
            else -> "UP"
        }

        val system = if (includeSystem) systemSnapshot() else emptyMap()
        return HealthAggregate(overallStatus = overall, components = components, system = system)
    }

    private fun checkDatabase(): Map<String, Any?> {
        val start = System.nanoTime()
        val dataSource = dataSourceProvider.ifAvailable ?: return mapOf("status" to "DISABLED")
        return try {
            dataSource.connection.use { connection ->
                val valid = connection.isValid(2)
                val meta = connection.metaData
                val details = linkedMapOf<String, Any?>(
                    "status" to if (valid) "UP" else "DOWN",
                    "latencyMs" to elapsedMs(start),
                    "product" to meta.databaseProductName,
                    "version" to meta.databaseProductVersion,
                    "driver" to meta.driverName,
                    "url" to sanitizeJdbcUrl(meta.url),
                )
                hikariPoolSnapshot(dataSource)?.let { details["pool"] = it }
                details
            }
        } catch (e: Exception) {
            mapOf(
                "status" to "DOWN",
                "latencyMs" to elapsedMs(start),
                "error" to (e::class.simpleName ?: "DatabaseError"),
            )
        }
    }

    private fun hikariPoolSnapshot(dataSource: DataSource): Map<String, Any?>? {
        val hikari = dataSource as? HikariDataSource ?: return null
        val mxBean = hikari.hikariPoolMXBean ?: return null
        return mapOf(
            "activeConnections" to mxBean.activeConnections,
            "idleConnections" to mxBean.idleConnections,
            "threadsAwaitingConnection" to mxBean.threadsAwaitingConnection,
            "totalConnections" to mxBean.totalConnections,
        )
    }

    private fun sanitizeJdbcUrl(url: String?): String? {
        if (url.isNullOrBlank()) return url
        return url.substringBefore('?')
    }

    private fun checkRedis(): Map<String, Any?> {
        val start = System.nanoTime()
        val template = redisTemplateProvider.ifAvailable ?: return mapOf("status" to "DISABLED")
        return try {
            val pong = template.connectionFactory?.connection?.use { connection ->
                connection.ping()
            }
            mapOf(
                "status" to if (pong.equals("PONG", ignoreCase = true)) "UP" else "DEGRADED",
                "latencyMs" to elapsedMs(start),
            )
        } catch (e: Exception) {
            mapOf(
                "status" to "DOWN",
                "latencyMs" to elapsedMs(start),
                "error" to (e::class.simpleName ?: "RedisError"),
            )
        }
    }

    private fun checkMonitoring(req: HttpServletRequest): Map<String, Any?> {
        val targets = linkedMapOf(
            "loki" to probeHttp(resolveLink(healthLinksProperties.monitoring.loki, req)),
            "prometheus" to probeHttp(resolveLink(healthLinksProperties.monitoring.prometheus, req)),
            "grafana" to probeHttp(resolveLink(healthLinksProperties.monitoring.grafana, req)),
            "lokiQuery" to probeHttp(resolveLink(healthLinksProperties.logs.lokiQuery, req)),
        )

        val statuses = targets.values.mapNotNull { it["status"] as? String }
        val status = when {
            statuses.all { it == "DISABLED" } -> "DISABLED"
            statuses.any { it == "DOWN" } -> "DEGRADED"
            statuses.any { it == "DEGRADED" } -> "DEGRADED"
            else -> "UP"
        }

        return mapOf(
            "status" to status,
            "targets" to targets,
        )
    }

    private fun probeHttp(url: String): Map<String, Any?> {
        if (url.isBlank()) return mapOf("status" to "DISABLED")

        val start = System.nanoTime()
        return runCatching {
            val connection = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 500
                readTimeout = 500
                instanceFollowRedirects = false
            }
            val code = connection.responseCode
            val status = when {
                code >= 500 -> "DOWN"
                code in 200..499 -> "UP"
                else -> "DEGRADED"
            }
            mapOf(
                "status" to status,
                "httpStatus" to code,
                "latencyMs" to elapsedMs(start),
                "url" to url,
            )
        }.getOrElse { e ->
            mapOf(
                "status" to "DOWN",
                "latencyMs" to elapsedMs(start),
                "url" to url,
                "error" to (e::class.simpleName ?: "HttpProbeError"),
            )
        }
    }

    private fun systemSnapshot(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        val heapUsed = runtime.totalMemory() - runtime.freeMemory()
        val rootStore = rootFileStore()

        return linkedMapOf(
            "jvm" to mapOf(
                "heapUsedBytes" to heapUsed,
                "heapMaxBytes" to runtime.maxMemory(),
                "processors" to runtime.availableProcessors(),
            ),
            "disk" to mapOf(
                "totalBytes" to (rootStore?.totalSpace ?: -1L),
                "usableBytes" to (rootStore?.usableSpace ?: -1L),
            ),
        )
    }

    private fun rootFileStore(): FileStore? {
        return runCatching { Files.getFileStore(Path.of("/")) }.getOrNull()
    }

    private fun appName(): String = environment.getProperty("spring.application.name") ?: "application"

    private fun appVersion(): String {
        return buildPropertiesProvider.ifAvailable?.version
            ?: environment.getProperty("app.version")
            ?: "unknown"
    }

    private fun buildInfo(): Map<String, Any?> {
        val build = buildPropertiesProvider.ifAvailable
        val git = gitPropertiesProvider.ifAvailable
        return linkedMapOf(
            "version" to appVersion(),
            "buildTime" to build?.time?.toString(),
            "gitCommitId" to git?.shortCommitId,
            "gitBranch" to git?.branch,
        )
    }

    private fun startedAtIso(): String = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().startTime).toString()

    private fun uptimeSeconds(): Long = ManagementFactory.getRuntimeMXBean().uptime / 1000

    private fun elapsedMs(startNano: Long): Long = (System.nanoTime() - startNano) / 1_000_000

    private fun publicLinks(req: HttpServletRequest): Map<String, Any> {
        return linkedMapOf(
            "docs" to linkedMapOf(
                "swagger" to resolveLink(healthLinksProperties.docs.swagger, req),
                "docs" to resolveLink(healthLinksProperties.docs.docs, req),
            ),
        )
    }

    private fun adminLinks(req: HttpServletRequest): Map<String, Any> {
        return linkedMapOf(
            "docs" to linkedMapOf(
                "swagger" to resolveLink(healthLinksProperties.docs.swagger, req),
                "docs" to resolveLink(healthLinksProperties.docs.docs, req),
            ),
            "monitoring" to linkedMapOf(
                "grafana" to resolveLink(healthLinksProperties.monitoring.grafana, req),
                "prometheus" to resolveLink(healthLinksProperties.monitoring.prometheus, req),
                "loki" to resolveLink(healthLinksProperties.monitoring.loki, req),
            ),
            "logs" to linkedMapOf(
                "lokiQuery" to resolveLink(healthLinksProperties.logs.lokiQuery, req),
                "grafanaExplore" to resolveLink(healthLinksProperties.logs.grafanaExplore, req),
            ),
        )
    }

    private fun resolveLink(template: String, req: HttpServletRequest): String {
        if (template.isBlank()) return template
        if (template.startsWith("http://") || template.startsWith("https://")) return template

        val scheme = forwardedFirst(req.getHeader("X-Forwarded-Proto")) ?: req.scheme
        val hostHeader = forwardedFirst(req.getHeader("X-Forwarded-Host"))
            ?: req.getHeader("Host")
            ?: req.serverName

        val host = hostHeader.substringBefore(":")
        val baseUrl = buildBaseUrl(req, scheme, hostHeader)
        return template
            .replace("{baseUrl}", baseUrl)
            .replace("{scheme}", scheme)
            .replace("{host}", host)
    }

    private fun buildBaseUrl(req: HttpServletRequest, scheme: String, hostHeader: String): String {
        if (hostHeader.contains(":")) return "$scheme://$hostHeader"

        val forwardedPort = forwardedFirst(req.getHeader("X-Forwarded-Port"))?.toIntOrNull()
        val port = forwardedPort ?: req.serverPort
        val includePort = !((scheme == "http" && port == 80) || (scheme == "https" && port == 443))
        return if (includePort) "$scheme://$hostHeader:$port" else "$scheme://$hostHeader"
    }

    private fun forwardedFirst(value: String?): String? =
        value?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }

    private data class HealthAggregate(
        val overallStatus: String,
        val components: Map<String, Map<String, Any?>>,
        val system: Map<String, Any>,
    )
}
