package com.myndstream.myndcoresdk.clients

import models.Category
import models.Playlist
import models.PlaylistWithSongs
import kotlinx.serialization.json.Json
import java.io.Serializable

interface ICatalogueClient : Serializable {
    suspend fun getCategories(): List<Category>
    suspend fun getCategory(categoryId: String): Category
    suspend fun getPlaylists(categoryId: String?): List<Playlist>
    suspend fun getPlaylist(playlistId: String): PlaylistWithSongs
}

class CatalogueClient(
    private val authedHttpClient: IHttpClient,
    private val baseUrl: String
) : ICatalogueClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getCategories(): List<Category> {
        val url = "$baseUrl/integration/catalogue/categories"
        println("CatalogueClient: Fetching categories from URL: $url")

        return try {
            val responseJson = authedHttpClient.get(
                url = url,
                headers = emptyMap()
            )
            val response = json.decodeFromString<List<Category>>(responseJson)
            println("CatalogueClient: Received categories: ${response.size} items")
            response
        } catch (e: Exception) {
            println("CatalogueClient: Failed to fetch categories: $e")
            throw e
        }
    }

    override suspend fun getCategory(categoryId: String): Category {
        val url = "$baseUrl/integration/catalogue/categories/$categoryId"
        println("CatalogueClient: Fetching category from URL: $url")

        return try {
            val responseJson = authedHttpClient.get(
                url = url,
                headers = emptyMap()
            )
            val response = json.decodeFromString<Category>(responseJson)
            println("CatalogueClient: Received category: ${response.name}")
            response
        } catch (e: Exception) {
            println("CatalogueClient: Failed to fetch category with ID $categoryId: $e")
            throw e
        }
    }

    override suspend fun getPlaylists(categoryId: String?): List<Playlist> {
        val url = "$baseUrl/integration/catalogue/playlists"
        println("CatalogueClient: Fetching playlists from URL: $url")

        return try {
            val responseJson = authedHttpClient.get(
                url = url,
                headers = emptyMap()
            )
            val response = json.decodeFromString<List<Playlist>>(responseJson)
            println("CatalogueClient: Received playlists: ${response.size} items")
            response
        } catch (e: Exception) {
            println("CatalogueClient: Failed to fetch playlists: $e")
            throw e
        }
    }

    override suspend fun getPlaylist(playlistId: String): PlaylistWithSongs {
        val url = "$baseUrl/integration/catalogue/playlists/$playlistId"
        println("CatalogueClient: Fetching playlist from URL: $url")

        return try {
            val responseJson = authedHttpClient.get(
                url = url,
                headers = emptyMap()
            )
            val response = json.decodeFromString<PlaylistWithSongs>(responseJson)
            println("CatalogueClient: Received playlist: ${response.playlist.name}")
            response
        } catch (e: Exception) {
            println("CatalogueClient: Failed to fetch playlist with ID $playlistId: $e")
            throw e
        }
    }
}