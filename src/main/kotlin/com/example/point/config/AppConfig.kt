package com.example.point.config

import com.example.point.bootstrapBus
import com.example.point.service.MessageBus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppConfig {
    @Bean
    fun messageBus(): MessageBus { return bootstrapBus() }
}
