package com.yaku.geo.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest

@Serializable
data class AuthRequest(val login: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val login: String)

@Serializable
data class SyncPoint(val latitude: Double, val longitude: Double, val timestamp: Long)

@Serializable
data class SyncRequest(val points: List<SyncPoint>)

@Serializable
data class SyncResponse(val points: List<SyncPoint>)

@Serializable
data class PasswordChangeRequest(val newPassword: String)

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("API IS ONLINE")
        }

        post("/register") {
            val request = call.receive<AuthRequest>()
            val success = transaction {
                if (Users.selectAll().where { Users.login eq request.login }.any()) return@transaction false
                Users.insert {
                    it[login] = request.login
                    it[passwordHash] = hashPassword(request.password)
                }
                true
            }
            if (success) {
                val token = generateToken(request.login)
                println("SUCCESS: User ${request.login} registered")
                call.respond(HttpStatusCode.Created, AuthResponse(token, request.login))
            } else {
                call.respond(HttpStatusCode.Conflict, "User exists")
            }
        }

        post("/login") {
            val request = call.receive<AuthRequest>()
            val found = transaction {
                Users.selectAll().where { 
                    (Users.login eq request.login) and (Users.passwordHash eq hashPassword(request.password))
                }.any()
            }
            if (found) {
                val token = generateToken(request.login)
                println("SUCCESS: User ${request.login} logged in")
                call.respond(AuthResponse(token, request.login))
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Wrong creds")
            }
        }

        authenticate("auth-jwt") {
            post("/change-password") {
                val login = call.principal<JWTPrincipal>()?.payload?.getClaim("login")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<PasswordChangeRequest>()
                
                val success = transaction {
                    Users.update({ Users.login eq login }) {
                        it[passwordHash] = hashPassword(request.newPassword)
                    } > 0
                }
                
                if (success) {
                    println("SUCCESS: Password changed for $login")
                    call.respond(HttpStatusCode.OK, "Password changed")
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to update")
                }
            }

            post("/sync") {
                val login = call.principal<JWTPrincipal>()?.payload?.getClaim("login")?.asString()
                if (login.isNullOrBlank()) {
                    return@post call.respond(HttpStatusCode.Unauthorized, "No login claim")
                }
                
                val request = call.receive<SyncRequest>()
                println("SYNC: Starting for $login (${request.points.size} points incoming)")

                val results = transaction {
                    val userId = Users.selectAll().where { Users.login eq login }.map { it[Users.id] }.firstOrNull()
                        ?: return@transaction null
                    
                    for (p in request.points) {
                        val exists = VisitedPoints.selectAll().where {
                            (VisitedPoints.userId eq userId) and 
                            (VisitedPoints.latitude eq p.latitude) and 
                            (VisitedPoints.longitude eq p.longitude)
                        }.any()
                        if (!exists) {
                            VisitedPoints.insert {
                                it[VisitedPoints.userId] = userId
                                it[latitude] = p.latitude
                                it[longitude] = p.longitude
                                it[timestamp] = p.timestamp
                            }
                        }
                    }

                    VisitedPoints.selectAll().where { VisitedPoints.userId eq userId }.map {
                        SyncPoint(it[VisitedPoints.latitude], it[VisitedPoints.longitude], it[VisitedPoints.timestamp])
                    }
                }
                
                if (results == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    println("SYNC: Finished for $login (${results.size} points outgoing)")
                    call.respond(SyncResponse(results))
                }
            }
        }
    }
}

fun hashPassword(password: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
