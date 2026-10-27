package com.wassollichkochen.server.routes

import com.wassollichkochen.server.auth.requireHouseholdAuth
import com.wassollichkochen.server.db.DatabaseFactory
import com.wassollichkochen.server.db.Households
import com.wassollichkochen.server.models.ClientCountResponse
import com.wassollichkochen.server.models.CreateHouseholdResponse
import com.wassollichkochen.server.models.HouseholdExistsResponse
import com.wassollichkochen.server.sync.SyncHub
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.security.SecureRandom
import java.time.Instant

private val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // ohne I,O,0,1
private val secureRandom = SecureRandom()

private fun generateCode(): String =
    (1..6).map { codeChars[secureRandom.nextInt(codeChars.length)] }.joinToString("")

private fun generateToken(): String {
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

fun Route.householdRoutes() {
    route("/households") {
        post {
            var code: String
            do {
                code = generateCode()
                val exists = DatabaseFactory.dbQuery {
                    Households.selectAll().where { Households.id eq code }.count() > 0
                }
            } while (exists)

            val token = generateToken()
            DatabaseFactory.dbQuery {
                Households.insert {
                    it[id] = code
                    it[apiToken] = token
                    it[createdAt] = Instant.now()
                }
            }
            call.respond(CreateHouseholdResponse(householdId = code, apiToken = token))
        }

        get("/{code}/exists") {
            val code = call.parameters["code"]?.uppercase()
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val exists = DatabaseFactory.dbQuery {
                Households.selectAll().where { Households.id eq code }.count() > 0
            }
            call.respond(HouseholdExistsResponse(exists))
        }

        // Vereinfachte Beitritts-Variante: Code ist das einzige Geheimnis.
        get("/{code}/token") {
            val code = call.parameters["code"]?.uppercase()
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val token = DatabaseFactory.dbQuery {
                Households.selectAll().where { Households.id eq code }.singleOrNull()?.get(Households.apiToken)
            } ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(CreateHouseholdResponse(householdId = code, apiToken = token))
        }

        get("/{code}/clients") {
            val code = call.parameters["code"]?.uppercase()
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            if (!call.requireHouseholdAuth(code)) return@get
            call.respond(ClientCountResponse(code, SyncHub.clientCount(code)))
        }
    }
}