package com.wassollichkochen.server.routes

import com.wassollichkochen.server.auth.requireHouseholdAuth
import com.wassollichkochen.server.db.DatabaseFactory
import com.wassollichkochen.server.db.ShoppingItems
import com.wassollichkochen.server.models.ShoppingItemDto
import com.wassollichkochen.server.models.SyncEvent
import com.wassollichkochen.server.sync.SyncHub
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant

private fun ResultRowToDto(row: org.jetbrains.exposed.sql.ResultRow) = ShoppingItemDto(
    id = row[ShoppingItems.id],
    name = row[ShoppingItems.name],
    quantity = row[ShoppingItems.quantity],
    unit = row[ShoppingItems.unit],
    isCompleted = row[ShoppingItems.isCompleted],
    category = row[ShoppingItems.category]
)

fun Route.shoppingRoutes() {
    route("/households/{householdId}/shopping-items") {

        get {
            val householdId = call.parameters["householdId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing householdId")
            if (!call.requireHouseholdAuth(householdId)) return@get

            val items = DatabaseFactory.dbQuery {
                ShoppingItems.selectAll().where { ShoppingItems.householdId eq householdId }
                    .map { ResultRowToDto(it) }
            }
            call.respond(items)
        }

        put("/{itemId}") {
            val householdId = call.parameters["householdId"]!!
            val itemId = call.parameters["itemId"]!!
            if (!call.requireHouseholdAuth(householdId)) return@put

            val dto = call.receive<ShoppingItemDto>()

            DatabaseFactory.dbQuery {
                val exists = ShoppingItems.selectAll().where { (ShoppingItems.id eq itemId) and (ShoppingItems.householdId eq householdId) }.count() > 0

                if (exists) {
                    ShoppingItems.update({ (ShoppingItems.id eq itemId) and (ShoppingItems.householdId eq householdId) }) {
                        it[name] = dto.name
                        it[quantity] = dto.quantity
                        it[unit] = dto.unit
                        it[isCompleted] = dto.isCompleted
                        it[category] = dto.category
                        it[updatedAt] = Instant.now()
                    }
                } else {
                    ShoppingItems.insert {
                        it[id] = itemId
                        it[ShoppingItems.householdId] = householdId
                        it[name] = dto.name
                        it[quantity] = dto.quantity
                        it[unit] = dto.unit
                        it[isCompleted] = dto.isCompleted
                        it[category] = dto.category
                    }
                }
            }

            SyncHub.broadcast(householdId, SyncEvent.ShoppingItemChanged(dto))
            call.respond(HttpStatusCode.OK)
        }

        delete("/{itemId}") {
            val householdId = call.parameters["householdId"]!!
            val itemId = call.parameters["itemId"]!!
            if (!call.requireHouseholdAuth(householdId)) return@delete

            DatabaseFactory.dbQuery {
                ShoppingItems.deleteWhere {
                    (ShoppingItems.id eq itemId) and (ShoppingItems.householdId eq householdId)
                }
            }

            SyncHub.broadcast(householdId, SyncEvent.ShoppingItemDeleted(itemId))
            call.respond(HttpStatusCode.OK)
        }
    }
}