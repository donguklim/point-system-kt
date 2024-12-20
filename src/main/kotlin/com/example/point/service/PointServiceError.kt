package com.example.point.service

import com.example.point.domain.commands.UserCommand
import com.example.point.domain.events.UserEvent

open class PointServiceError(errorMessage: String): Exception(errorMessage) {
}

class InvalidCommandError(command: UserCommand): PointServiceError(
    "user - ${command.userId}: Unknown command - ${command::class.qualifiedName}"
)

class InvalidEventError(event: UserEvent): PointServiceError(
    "user - ${event.userId}: Unknown event - ${event::class.qualifiedName}"
)
