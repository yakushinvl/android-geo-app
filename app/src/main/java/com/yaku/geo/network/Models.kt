package com.yaku.geo.network

import kotlinx.serialization.Serializable

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
