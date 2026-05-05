package io.soo.springboot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@ConfigurationPropertiesScan
@EnableScheduling
@SpringBootApplication
class CoreApiApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<CoreApiApplication>(*args)
        }
    }
}
