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
 * Eigenes Stylesheet für die offizielle Seite: transparenter Hintergrund (dahinter liegt der App-Hintergrund),
 * Formularflächen als Mattglas, Akzent in Orange, deutlicher Fokus für die Fernbedienung. Absichtlich nur
 * allgemeine Selektoren, damit eine geänderte Seite nicht unbenutzbar wird.
 */
private const val GLASS_CSS = """
html, body, main, header, footer, #root, #__next, [class*="app"], [class*="page"], [class*="layout"] {
  background: transparent !important;
  background-color: transparent !important;
}
body { color: #f2f2f5 !important; }
form, [class*="card"], [class*="modal"], [class*="panel"], [class*="container"] > div:has(form) {
  background: rgba(255, 255, 255, 0.07) !important;
  border: 1px solid rgba(255, 255, 255, 0.18) !important;
  border-radius: 24px !important;
  backdrop-filter: blur(24px) saturate(140%) !important;
  -webkit-backdrop-filter: blur(24px) saturate(140%) !important;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.45) !important;
}
form { padding: 32px !important; }
input, select, textarea {
  background: rgba(255, 255, 255, 0.08) !important;
  color: #f2f2f5 !important;
  border: 1px solid rgba(255, 255, 255, 0.22) !important;
  border-radius: 14px !important;
}
input::placeholder { color: #a0a0ae !important; }
button[type="submit"], [class*="button--primary"], [class*="primary"] button {
  background: #f47521 !important;
  color: #ffffff !important;
  border: none !important;
  border-radius: 14px !important;
}
a { color: #ffb27a !important; }
*:focus, *:focus-visible {
  outline: 3px solid #f47521 !important;
  outline-offset: 3px !important;
}
"""

/** Fügt das Stylesheet einmalig ein; liest nichts aus der Seite. */
private fun WebView.injectGlass() {
    val css = GLASS_CSS.replace("\\", "\\\\").replace("`", "\\`")
    evaluateJavascript(
        "(function(){if(document.getElementById('crtv-glass'))return;" +
            "var s=document.createElement('style');s.id='crtv-glass';s.textContent=`$css`;" +
            "(document.head||document.documentElement).appendChild(s);})();",
        null,
    )
}

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
                // Transparent, damit der weichgezeichnete App-Hintergrund durch die Glasflächen scheint.
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                cookies.setAcceptCookie(true)
                cookies.setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = check()
                    override fun onPageCommitVisible(view: WebView, url: String) = view.injectGlass()
                    override fun onPageFinished(view: WebView, url: String) {
                        view.injectGlass()
                        check()
                    }
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
