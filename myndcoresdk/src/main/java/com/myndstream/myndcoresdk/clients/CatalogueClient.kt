package com.myndstream.myndcoresdk.clients

import models.Category
import models.Playlist
import models.PlaylistWithSongs
import kotlinx.serialization.json.Json
import java.io.Serializable

interface ICatalogueClient : Serializable {
    suspend fun getCategories(): Result<List<Category>>
    suspend fun getCategory(categoryId: String): Result<Category>
    suspend fun getPlaylists(categoryId: String?): Result<List<Playlist>>
    suspend fun getPlaylist(playlistId: String): Result<PlaylistWithSongs>
}

class CatalogueClient(
    private val authedHttpClient: IHttpClient,
    private val baseUrl: String
) : ICatalogueClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getCategories(): Result<List<Category>> =
        runCatching {
            val url = "$baseUrl/integration/catalogue/categories"
            println("Fetching categories from $url")
            val responseJson = authedHttpClient.get(url, headers = emptyMap())
            json.decodeFromString<List<Category>>(responseJson).also {
                println("Received ${it.size} categories")
            }
        }

    override suspend fun getCategory(categoryId: String): Result<Category> =
        runCatching {
            val url = "$baseUrl/integration/catalogue/categories/$categoryId"
            println("Fetching category from $url")
            val responseJson = authedHttpClient.get(url, headers = emptyMap())
            json.decodeFromString<Category>(responseJson).also {
                println("Received category: ${it.name}")
            }
        }

    override suspend fun getPlaylists(categoryId: String?): Result<List<Playlist>> =
        runCatching {
            val url = if (categoryId != null) {
                "$baseUrl/integration/catalogue/playlists?categoryId=$categoryId"
            } else {
                "$baseUrl/integration/catalogue/playlists"
            }
            println("Fetching playlists from $url (filter: $categoryId)")
            val responseJson = authedHttpClient.get(url, headers = emptyMap())
            json.decodeFromString<List<Playlist>>(responseJson).also {
                println("Received ${it.size} playlists")
            }
        }

    override suspend fun getPlaylist(playlistId: String): Result<PlaylistWithSongs> =
        runCatching {
            val url = "$baseUrl/integration/catalogue/playlists/$playlistId"
            println("Fetching playlist from $url")
            val responseJson = authedHttpClient.get(url, headers = emptyMap())
            json.decodeFromString<PlaylistWithSongs>(responseJson).also {
                println("Received playlist: ${it.playlist.name}")
            }
        }
}
