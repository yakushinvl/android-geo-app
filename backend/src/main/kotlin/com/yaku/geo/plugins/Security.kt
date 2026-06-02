package com.yaku.geo.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

private const val SECRET = "STATIC_SECRET_123456789"

fun Application.configureSecurity() {
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                JWT.require(Algorithm.HMAC256(SECRET)).build()
            )
            validate { credential ->
                val login = credential.payload.getClaim("login").asString()
                if (!login.isNullOrBlank()) {
                    JWTPrincipal(credential.payload)
                } else {
                    println("AUTH ERROR: login claim missing in token")
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Invalid or expired token")
            }
        }
    }
}

fun generateToken(login: String): String {
    return JWT.create()
        .withClaim("login", login)
        .withExpiresAt(java.util.Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365)) // 1 year
        .sign(Algorithm.HMAC256(SECRET))
}
