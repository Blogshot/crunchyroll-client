package com.github.blogshot.crtv.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.blogshot.crtv.ui.theme.glass

private const val LOGIN_URL = "https://www.crunchyroll.com/login"
private const val COOKIE_HOST = "https://www.crunchyroll.com"

/**
 * Offizielle Crunchyroll-Anmeldung in einer WebView innerhalb der App. Sobald die Seite das Sitzungs-Cookie
 * `etp_rt` setzt, wird es an [onSession] übergeben und gegen Token getauscht. Die App sieht das Passwort nie.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLogin(userAgent: String, onSession: (String) -> Unit, onCancel: () -> Unit) {
    val cookies = remember { CookieManager.getInstance() }
    val delivered = remember { booleanArrayOf(false) }
    var webView: WebView? = null

    fun check() {
        if (delivered[0]) return
        val etpRt = cookies.getCookie(COOKIE_HOST)
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("etp_rt=") }
            ?.substringAfter("etp_rt=")
            ?.takeIf { it.isNotEmpty() }
            ?: return
        delivered[0] = true
        onSession(etpRt)
    }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onCancel()
    }

    DisposableEffect(Unit) {
        // Frisch starten: keine alte Website-Sitzung übernehmen.
        cookies.removeAllCookies(null)
        onDispose {
            // Die Sitzung lebt danach nur noch verschlüsselt im Token-Speicher, nicht im WebView-Cookie-Speicher.
            cookies.removeAllCookies(null)
            cookies.flush()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize().padding(32.dp).glass(RoundedCornerShape(24.dp)).padding(4.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = userAgent
                cookies.setAcceptCookie(true)
                cookies.setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = check()
                    override fun onPageFinished(view: WebView, url: String) = check()
                }
                isFocusable = true
                isFocusableInTouchMode = true
                loadUrl(LOGIN_URL)
                requestFocus()
                webView = this
            }
        },
        update = { webView = it },
    )
}
