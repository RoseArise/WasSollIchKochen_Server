package com.wassollichkochen.server

import com.wassollichkochen.server.db.DatabaseFactory
import com.wassollichkochen.server.db.Households
import com.wassollichkochen.server.routes.householdRoutes
import com.wassollichkochen.server.routes.shoppingRoutes
import com.wassollichkochen.server.sync.SyncHub
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.config.tryGetString
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import kotlin.time.Duration.Companion.seconds

fun Application.module() {
    val dbUrl = System.getenv("DATABASE_URL") ?: environment.config.tryGetString("database.url") ?: "jdbc:postgresql://localhost:5432/wassollichkochen"
    val dbUser = System.getenv("DATABASE_USER") ?: environment.config.tryGetString("database.user") ?: "wsik"
    val dbPassword = System.getenv("DATABASE_PASSWORD") ?: environment.config.tryGetString("database.password") ?: "changeme"
    DatabaseFactory.init(dbUrl, dbUser, dbPassword)

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(CallLogging)
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unbehandelter Fehler", cause)
            call.respondText(text = "Interner Fehler", status = HttpStatusCode.InternalServerError)
        }
    }

    routing {
        householdRoutes()
        shoppingRoutes()

        webSocket("/households/{householdId}/sync") {
            val householdId = call.parameters["householdId"] ?: return@webSocket
            val token = call.request.queryParameters["token"]

            val valid = DatabaseFactory.dbQuery {
                Households.selectAll().where { Households.id eq householdId }
                    .singleOrNull()?.get(Households.apiToken) == token
            }
            if (!valid) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                return@webSocket
            }

            SyncHub.register(householdId, this)
            try {
                for (frame in incoming) { /* nur Empfang */ }
            } finally {
                SyncHub.unregister(householdId, this)
            }
        }
    }
}