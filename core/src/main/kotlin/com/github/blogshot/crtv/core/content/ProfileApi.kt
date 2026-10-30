package com.github.blogshot.crtv.core.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class Profile(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val isPrimary: Boolean,
    val isSelected: Boolean,
)

interface ProfileApi {
    suspend fun profiles(): List<Profile>
}

/** `GET /accounts/v1/me/multiprofile`. [http] muss den AuthInterceptor enthalten. */
class CrunchyrollProfileApi(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://www.crunchyroll.com",
    private val avatarBaseUrl: String = "https://static.crunchyroll.com/assets/avatar/170x170",
) : ProfileApi {

    override suspend fun profiles(): List<Profile> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/accounts/v1/me/multiprofile"
        val body = http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code, "/accounts/v1/me/multiprofile", describeFailure(response))
            response.body?.string().orEmpty()
        }
        parse(body)
    }

    fun parse(body: String): List<Profile> {
        val root = json.parseToJsonElement(body).jsonObject
        return root["profiles"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("profile_id") ?: return@mapNotNull null
            Profile(
                id = id,
                name = o.str("profile_name") ?: o.str("username").orEmpty(),
                avatarUrl = o.str("avatar")?.let { if (it.startsWith("http")) it else "$avatarBaseUrl/$it" },
                isPrimary = o.bool("is_primary"),
                isSelected = o.bool("is_selected"),
            )
        }
    }

    private fun JsonObject.str(key: String) = runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
    private fun JsonObject.bool(key: String) = runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: false

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
