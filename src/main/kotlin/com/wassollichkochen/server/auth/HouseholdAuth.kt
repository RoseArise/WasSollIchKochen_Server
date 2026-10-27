package com.wassollichkochen.server.auth

import com.wassollichkochen.server.db.DatabaseFactory
import com.wassollichkochen.server.db.Households
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import org.jetbrains.exposed.sql.selectAll

/**
 * Prüft, ob der Bearer-Token im Authorization-Header zum householdId in der URL passt.
 * Gibt bei Erfolg true zurück; bei Misserfolg wird direkt mit 401 geantwortet.
 */
suspend fun ApplicationCall.requireHouseholdAuth(householdId: String): Boolean {
    val header = request.headers["Authorization"]
    val token = header?.removePrefix("Bearer ")?.trim()
    if (token.isNullOrEmpty()) {
        respond(HttpStatusCode.Unauthorized, "Missing Authorization header")
        return false
    }

    val valid = DatabaseFactory.dbQuery {
        Households.selectAll().where { Households.id eq householdId }
            .singleOrNull()
            ?.get(Households.apiToken) == token
    }

    if (!valid) {
        respond(HttpStatusCode.Unauthorized, "Invalid token")
        return false
    }
    return true
}