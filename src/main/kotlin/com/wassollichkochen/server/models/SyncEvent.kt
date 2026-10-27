package com.wassollichkochen.server.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SyncEvent {
    @Serializable
    @SerialName("shopping_item_changed")
    data class ShoppingItemChanged(val item: ShoppingItemDto) : SyncEvent()

    @Serializable
    @SerialName("shopping_item_deleted")
    data class ShoppingItemDeleted(val itemId: String) : SyncEvent()
}