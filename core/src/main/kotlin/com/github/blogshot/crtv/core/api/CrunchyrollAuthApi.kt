package com.github.blogshot.crtv.core.api

import com.github.blogshot.crtv.core.auth.AuthApi
import com.github.blogshot.crtv.core.auth.AuthTokens
import com.github.blogshot.crtv.core.auth.DeviceCode
import com.github.blogshot.crtv.core.auth.DevicePoll
import com.github.blogshot.crtv.core.auth.LoginResult
import com.github.blogshot.crtv.core.auth.TokenResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Token-Endpunkte (`/auth/v1/...`). Die genaue Form des Gerätecode-Flows wird in M0 gegen die echte API
 * geprüft; Abweichungen werden nur hier angepasst.
 */
class CrunchyrollAuthApi(
    private val http: OkHttpClient,
    private val config: ClientConfig,
    private val credentials: ClientCredentialsProvider,
    private val clock: () -> Long = System::currentTimeMillis,
) : AuthApi {

    override suspend fun passwordLogin(username: String, password: String): LoginResult {
        val form = FormBody.Builder()
            .add("grant_type", "password")
            .add("username", username)
            .add("password", password)
            .add("scope", "offline_access")
            .addDevice()
            .build()
        return try {
            val (code, body) = post("/auth/v1/token", form)
            when {
                code in 200..299 -> LoginResult.Success(json.decodeFromString<TokenResponse>(body).toTokens())
                errorCode(body) == "invalid_client" -> {
                    credentials.invalidate()
                    LoginResult.MissingClientId
                }
                errorCode(body) == "invalid_grant" || code == 401 -> LoginResult.WrongCredentials
                else -> LoginResult.Failed(IOException("HTTP $code"))
            }
        } catch (e: MissingClientCredentialsException) {
            LoginResult.MissingClientId
        } catch (e: IOException) {
            LoginResult.Failed(e)
        } catch (e: kotlinx.serialization.SerializationException) {
            LoginResult.Failed(e)
        }
    }

    override suspend fun refresh(refreshToken: String, profileId: String?, switchProfile: Boolean): TokenResult {
        val form = FormBody.Builder()
            // Profilwechsel hat bei Crunchyroll einen eigenen Grant-Typ (in M0 gegen die echte API prüfen).
            .add("grant_type", if (switchProfile) "refresh_token_profile_id" else "refresh_token")
            .add("refresh_token", refreshToken)
            .add("scope", "offline_access")
            .addDevice()
            .apply { if (profileId != null) add("profile_id", profileId) }
            .build()
        return try {
            val (code, body) = post("/auth/v1/token", form)
            when {
                code in 200..299 -> TokenResult.Success(json.decodeFromString<TokenResponse>(body).toTokens())
                errorCode(body) == "invalid_grant" -> TokenResult.InvalidGrant
                else -> TokenResult.Transient(IOException("HTTP $code"))
            }
        } catch (e: IOException) {
            TokenResult.Transient(e)
        } catch (e: kotlinx.serialization.SerializationException) {
            TokenResult.Transient(e)
        }
    }

    override suspend fun requestDeviceCode(): DeviceCode {
        val (code, body) = post("/auth/v1/device/code", FormBody.Builder().addDevice().build())
        if (code !in 200..299) throw IOException("Gerätecode: HTTP $code")
        val r = json.decodeFromString<DeviceCodeResponse>(body)
        return DeviceCode(r.deviceCode, r.userCode, config.activationUrl, r.expiresIn, r.interval ?: 5)
    }

    override suspend fun pollDeviceCode(deviceCode: String): DevicePoll = try {
        val form = FormBody.Builder().add("device_code", deviceCode).addDevice().build()
        val (code, body) = post("/auth/v1/device/token", form)
        when {
            code in 200..299 -> DevicePoll.Approved(json.decodeFromString<TokenResponse>(body).toTokens())
            errorCode(body) == "expired_token" -> DevicePoll.Expired
            code in 400..499 -> DevicePoll.Pending
            else -> DevicePoll.Failed(IOException("HTTP $code"))
        }
    } catch (e: IOException) {
        DevicePoll.Failed(e)
    }

    private fun FormBody.Builder.addDevice() = apply {
        add("device_id", config.deviceId)
        add("device_name", config.deviceName)
        add("device_type", config.deviceType)
    }

    private suspend fun post(path: String, form: FormBody): Pair<Int, String> {
        val client = credentials.get()
        return withContext(Dispatchers.IO) { postBlocking(path, form, client) }
    }

    private fun postBlocking(path: String, form: FormBody, client: ClientCredentials): Pair<Int, String> {
        val request = Request.Builder()
            .url(config.baseUrl + path)
            .header("Authorization", Credentials.basic(client.clientId, client.clientSecret))
            .header("User-Agent", config.userAgent)
            .post(form)
            .build()
        return http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
    }

    private fun errorCode(body: String): String? =
        runCatching { json.decodeFromString<ErrorResponse>(body).error }.getOrNull()

    private fun TokenResponse.toTokens() = AuthTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtMs = clock() + expiresIn * 1000,
        accountId = accountId,
        profileId = profileId,
    )

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_in") val expiresIn: Long,
        @SerialName("account_id") val accountId: String? = null,
        @SerialName("profile_id") val profileId: String? = null,
    )

    @Serializable
    private data class DeviceCodeResponse(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("expires_in") val expiresIn: Long,
        val interval: Long? = null,
    )

    @Serializable
    private data class ErrorResponse(val error: String? = null, val code: String? = null)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
