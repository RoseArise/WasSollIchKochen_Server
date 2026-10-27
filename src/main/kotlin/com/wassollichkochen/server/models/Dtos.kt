package com.wassollichkochen.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ShoppingItemDto(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val isCompleted: Boolean,
    val category: String
) {
    init {
        require(id.isNotBlank() && id.length <= 32) { "Invalid id" }
        require(name.isNotBlank() && name.length <= 200) { "Invalid name" }
        require(category.isNotBlank() && category.length <= 100) { "Invalid category" }
    }
}

@Serializable
data class CreateHouseholdResponse(
    val householdId: String,
    val apiToken: String
)

@Serializable
data class HouseholdExistsResponse(
    val exists: Boolean
)