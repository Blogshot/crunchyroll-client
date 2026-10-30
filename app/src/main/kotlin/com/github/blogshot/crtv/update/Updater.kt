package com.github.blogshot.crtv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.github.blogshot.crtv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val versionCode: Int, val versionName: String) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class Failed(val message: String) : UpdateState
    /** Nur nach manueller Prüfung, damit man sieht, dass geprüft wurde. */
    data class UpToDate(val versionName: String) : UpdateState
}

/**
 * Selbst-Update aus den GitHub-Releases des Repos. Die CI legt pro Kanal (Tag) `crtv.apk` und `version.json` ab;
 * ist dort ein höherer versionCode als der eigene, bietet die App das Update an. Installiert wird über den
 * System-Installer, Android fragt also immer noch selbst nach.
 */
class Updater(private val context: Context, private val http: OkHttpClient) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
    private var lastCheckMs = 0L

    private val base = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/download/${BuildConfig.UPDATE_CHANNEL}"

    @Serializable
    private data class Version(val versionCode: Int, val versionName: String = "")

    /** Höchstens alle 2 Minuten; automatische Prüfungen bleiben bei Fehlern still. */
    suspend fun checkIfDue(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckMs < 2 * 60_000) return
        if (_state.value is UpdateState.Downloading) return
        lastCheckMs = now
        val remote = runCatching {
            withContext(Dispatchers.IO) {
                http.newCall(Request.Builder().url("$base/version.json").build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    json.decodeFromString<Version>(r.body?.string().orEmpty())
                }
            }
        }.getOrElse {
            if (force) _state.value = UpdateState.Failed("Prüfung fehlgeschlagen: ${it.message}")
            return
        }
        _state.value = when {
            remote.versionCode > BuildConfig.VERSION_CODE -> UpdateState.Available(remote.versionCode, remote.versionName)
            force -> UpdateState.UpToDate(BuildConfig.VERSION_NAME)
            else -> _state.value
        }
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    /** Lädt das APK und öffnet den System-Installer. */
    suspend fun install() {
        if (!canInstall()) {
            // Einmalig „Unbekannte Apps installieren“ für diese App erlauben.
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "crtv.apk")
        try {
            _state.value = UpdateState.Downloading(0)
            withContext(Dispatchers.IO) {
                http.newCall(Request.Builder().url("$base/crtv.apk").build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    val body = r.body ?: throw IOException("Leere Antwort")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        apk.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) _state.value = UpdateState.Downloading((done * 100 / total).toInt())
                            }
                        }
                    }
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            _state.value = UpdateState.Idle
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    private fun canInstall() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
