package com.example.point.api

import com.example.point.bootstrapBus
import com.example.point.service.MessageBus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/hello")
class HelloWorldController(
    private val bus: MessageBus = bootstrapBus()
) {
    @GetMapping
    fun index(): ResponseEntity<String> {
        val hello = "Hello World!"
        return ResponseEntity.ok(hello)
    }
}
