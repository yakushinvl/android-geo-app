package com.yaku.geo.plugins

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val login = varchar("login", 12).uniqueIndex()
    val passwordHash = varchar("password_hash", 128)
    override val primaryKey = PrimaryKey(id)
}

object VisitedPoints : Table("visited_points") {
    val id = long("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val timestamp = long("timestamp")
    override val primaryKey = PrimaryKey(id)
}

fun Application.configureDatabase() {
    Database.connect("jdbc:sqlite:/app/data/geo.db", "org.sqlite.JDBC")
    
    transaction {
        SchemaUtils.create(Users, VisitedPoints)
    }
}
