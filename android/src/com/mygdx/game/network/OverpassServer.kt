package com.mygdx.game.network

import java.util.UUID

data class OverpassServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val isDefault: Boolean = false
) {
    companion object {
        val DEFAULT = OverpassServer(
            id = "default",
            name = "Global (overpass-api.de)",
            url = "https://overpass-api.de/api/interpreter",
            isDefault = true
        )
    }
}
