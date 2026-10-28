package com.classpulse.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val jwt: Jwt = Jwt(),
    val upload: Upload = Upload(),
    val bootstrap: Bootstrap = Bootstrap(),
    val cors: Cors = Cors(),
) {
    data class Jwt(
        val secret: String = "",
        val expirationHours: Long = 168,
        val requireConfigured: Boolean = false,
    )

    data class Upload(
        val dir: String = "./data/uploads",
        val maxBytes: Long = 10 * 1024 * 1024,
    )

    data class Bootstrap(
        val teacherUsername: String = "",
        val teacherPassword: String = "",
        val teacherName: String = "",
    )

    data class Cors(
        val allowedOrigins: String = "",
    )
}
