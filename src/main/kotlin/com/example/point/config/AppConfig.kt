package com.example.point.config

import com.example.point.boostrapRedisPointCache
import com.example.point.bootstrapBus
import com.example.point.bootstrapExposedPointRepository
import com.example.point.infrastructure.adapters.ExposedPointRepository
import com.example.point.infrastructure.adapters.RedisPointCache
import com.example.point.service.MessageBus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class AppConfig(
    private val appPointRepository: ExposedPointRepository = bootstrapExposedPointRepository(),
    private val appPointCache: RedisPointCache = boostrapRedisPointCache()
) {
    @Bean
    fun pointRepository(): ExposedPointRepository {
        return appPointRepository
    }

    @Bean
    fun pointCache(): RedisPointCache {
        return appPointCache
    }

    @Bean
    fun messageBus(): MessageBus { return bootstrapBus(cache = appPointCache, repository = appPointRepository) }
}
