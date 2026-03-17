package io.soo.springboot.core.domain

import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.env.Environment
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.FileStore
import java.nio.file.Path
import javax.sql.DataSource

@Service
class HealthSnapshotService(
    private val environment: Environment,
    private val dataSourceProvider: ObjectProvider<DataSource>,
    private val redisTemplateProvider: ObjectProvider<StringRedisTemplate>,
) {
    fun publicSummary(): Map<String, Any> {
        val overall = evaluateOverallStatus(includeSystem = false)
        return linkedMapOf(
            "status" to overall.overallStatus,
            "application" to appName(),
            "uptimeSec" to uptimeSeconds(),
        )
    }

    fun adminDetails(): Map<String, Any> {
        val overall = evaluateOverallStatus(includeSystem = true)
        return linkedMapOf(
            "status" to overall.overallStatus,
            "application" to appName(),
            "profiles" to environment.activeProfiles.toList(),
            "uptimeSec" to uptimeSeconds(),
            "components" to overall.components,
            "system" to overall.system,
        )
    }

    private fun evaluateOverallStatus(includeSystem: Boolean): HealthAggregate {
        val components = linkedMapOf<String, Map<String, Any?>>()
        val db = checkDatabase()
        components["database"] = db
        val redis = checkRedis()
        components["redis"] = redis

        val statuses = listOf(db["status"], redis["status"]).mapNotNull { it as? String }
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
                mapOf(
                    "status" to if (valid) "UP" else "DOWN",
                    "latencyMs" to elapsedMs(start),
                )
            }
        } catch (e: Exception) {
            mapOf(
                "status" to "DOWN",
                "latencyMs" to elapsedMs(start),
                "error" to (e::class.simpleName ?: "DatabaseError"),
            )
        }
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

    private fun uptimeSeconds(): Long = ManagementFactory.getRuntimeMXBean().uptime / 1000

    private fun elapsedMs(startNano: Long): Long = (System.nanoTime() - startNano) / 1_000_000

    private data class HealthAggregate(
        val overallStatus: String,
        val components: Map<String, Map<String, Any?>>,
        val system: Map<String, Any>,
    )
}
