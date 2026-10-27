package com.wassollichkochen.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Households : Table("households") {
    val id = varchar("id", 6)
    val apiToken = varchar("api_token", 64)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ShoppingItems : Table("shopping_items") {
    val id = varchar("id", 32)
    val householdId = varchar("household_id", 6).references(Households.id)
    val name = varchar("name", 200)
    val quantity = double("quantity")
    val unit = varchar("unit", 50)
    val isCompleted = bool("is_completed")
    val category = varchar("category", 100)
    override val primaryKey = PrimaryKey(id)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}