package com.example.point.infrastructure.database

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

enum class PointType(val value: String) {
    CHARGE("charge"),
    CONSUME("consume"),
    REFUND("refund"),
}

object PointEvents : LongIdTable("point_events") {
    val userId = integer("user_id")
    val transactionCode = varchar("transaction_code", 63)
    val numPoints = integer("num_points")
    val type = varchar("type", 15)
    val title = varchar("name", 127)
    val description = varchar("description", 255).default("")
    val transactionAt = datetime("transaction_at").index()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    val userTypeTransactionAT =
        index(
            "user_type_transaction_at",
            false,
            *arrayOf(userId, type, transactionAt),
        )
    val userTransactionAT =
        index(
            "user_transaction_at",
            false,
            *arrayOf(userId, transactionAt),
        )
    val userTransactionCode =
        uniqueIndex(
            "user_transaction_code",
            *arrayOf(userId, transactionCode),
        )
}

object PointDetails : LongIdTable("point_details") {
    val userId = integer("user_id")
    val type = varchar("type", 15)
    val chargeId = long("charge_id")
    val expireAt = datetime("expire_at")
    val eventId = reference("event_id", PointEvents.id, onDelete = ReferenceOption.CASCADE)
    val numPoints = integer("num_points")
    val transactionAt = datetime("transaction_at")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    val pointFetchIndex =
        index(
            "point_fetch_index",
            false,
            *arrayOf(userId, expireAt, chargeId),
        )
}
