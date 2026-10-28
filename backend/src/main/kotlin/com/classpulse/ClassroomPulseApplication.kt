package com.classpulse

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ClassroomPulseApplication

fun main(args: Array<String>) {
    runApplication<ClassroomPulseApplication>(*args)
}
