package com.example.point.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
object AppProperties {

    @Value("\${spring.datasource.url}")
    lateinit var datasourceUrl: String

    @Value("\${spring.datasource.username}")
    lateinit var datasourceUsername: String

    @Value("\${spring.datasource.password}")
    lateinit var datasourcePassword: String

    @Value("\${redis.host}")
    lateinit var redisHost: String

    @Value("\${redis.port}")
    lateinit var redisPort: String

    @Value("\${redis.password}")
    lateinit var redisPassword: String


}