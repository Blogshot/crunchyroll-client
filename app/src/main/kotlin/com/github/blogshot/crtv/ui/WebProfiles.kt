package com.github.blogshot.crtv.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
import com.github.blogshot.crtv.core.content.CrunchyrollProfileApi
import com.github.blogshot.crtv.core.content.Profile
import com.github.blogshot.crtv.core.content.ProfileProbe
import com.github.blogshot.crtv.ui.theme.glass
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient

private const val PROFILES_URL = "https://www.crunchyroll.com/profiles"
private const val COOKIE_HOST = "https://www.crunchyroll.com"

class WebProfileResult(
    /** Profil, das auf der Website gewählt wurde (aus dem Token-Request der Seite). */
    val selectedProfileId: String?,
    /** Profilliste, wie die Website sie bekommen hat. */
    val profiles: List<Profile>?,
    /** Was die Seite an Konto- und Token-Endpunkte geschickt hat: nur Pfade, Status, Feld- und Claim-Namen. */
    val lines: List<String>,
    val cookie: String?,
)

/**
 * Hängt sich in `fetch` und `XMLHttpRequest` der Seite und meldet Aufrufe an `/auth/v1/` und `/accounts/v1/`.
 * An die App gehen nur Methode, URL, Header-Namen, Formularfeld-Namen (Werte nur für grant_type, scope, profile_id),
 * Status, das Access-Token für die Claim-Anzeige und bei der Profilliste die Antwort. Die App sendet nichts davon weiter.
 */
private const val HOOK_JS = """
(function(){
  if (window.__crtvHook) return; window.__crtvHook = true;
  function rep(o){ try { CrtvBridge.report(JSON.stringify(o)); } catch(e) {} }
  function interesting(u){ return /\/(auth|accounts)\/v1\//.test(u || ''); }
  function names(h){ var n=[]; try { if (h && typeof h.forEach === 'function') h.forEach(function(v,k){ n.push(String(k).toLowerCase()); }); else if (h) n = Object.keys(h).map(function(k){ return k.toLowerCase(); }); } catch(e) {} return n; }
  function auth(h){ try { if (h && typeof h.get === 'function') return h.get('authorization'); if (h) for (var k in h) if (k.toLowerCase()==='authorization') return h[k]; } catch(e) {} return null; }
  function form(b){ try { if (!b) return null; var p = new URLSearchParams(typeof b === 'string' ? b : b.toString()); var o={keys:[]}; p.forEach(function(v,k){ o.keys.push(k); if (k==='grant_type'||k==='scope'||k==='profile_id') o[k]=v; }); return o; } catch(e) { return null; } }
  function finish(info, status, text){
    info.status = status;
    if (text && /multiprofile/.test(info.url)) info.response = text.slice(0, 50000);
    if (text && /\/auth\/v1\/token/.test(info.url)) { try { var j = JSON.parse(text); info.token = j.access_token || null; info.tokenProfile = j.profile_id || null; } catch(e) {} }
    rep(info);
  }
  var of = window.fetch;
  window.fetch = function(input, init){
    var url = typeof input === 'string' ? input : (input && input.url) || String(input);
    var p = of.apply(this, arguments);
    if (interesting(url)) {
      var h = (init && init.headers) || (input && input.headers);
      var info = { via:'fetch', url:url, method:(init && init.method) || (input && input.method) || 'GET', headers:names(h), auth:auth(h), credentials:(init && init.credentials) || null, body:form(init && init.body) };
      p.then(function(r){ r.clone().text().then(function(t){ finish(info, r.status, t); }, function(){ finish(info, r.status, null); }); }, function(){ finish(info, -1, null); });
    }
    return p;
  };
  var xo = XMLHttpRequest.prototype.open, xs = XMLHttpRequest.prototype.send, xh = XMLHttpRequest.prototype.setRequestHeader;
  XMLHttpRequest.prototype.open = function(m, u){ this.__crtv = { via:'xhr', method:m, url:String(u), headers:[], auth:null }; return xo.apply(this, arguments); };
  XMLHttpRequest.prototype.setRequestHeader = function(k, v){ if (this.__crtv) { this.__crtv.headers.push(String(k).toLowerCase()); if (String(k).toLowerCase()==='authorization') this.__crtv.auth = v; } return xh.apply(this, arguments); };
  XMLHttpRequest.prototype.send = function(b){
    var info = this.__crtv, x = this;
    if (info && interesting(info.url)) { info.body = form(b); info.credentials = x.withCredentials ? 'include' : null; x.addEventListener('loadend', function(){ finish(info, x.status, typeof x.responseText === 'string' ? x.responseText : null); }); }
    return xs.apply(this, arguments);
  };
})();
"""

private val json = Json { ignoreUnknownKeys = true }
private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/**
 * Profilwahl über die offizielle Website in einer WebView, mit der Sitzung der App. Die Seite lädt und wechselt
 * Profile selbst; die App liest mit, welches Profil gewählt wurde, und holt danach ein eigenes Token dafür.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun WebProfiles(userAgent: String, etpRt: String, onResult: (WebProfileResult) -> Unit) {
    val cookies = remember { CookieManager.getInstance() }
    val state = remember { WebProfileCollector() }
    val main = remember { Handler(Looper.getMainLooper()) }
    var webView: WebView? = null

    fun finish() {
        if (state.done) return
        state.done = true
        val cookie = cookies.getCookie(COOKIE_HOST)?.split(';')?.map { it.trim() }
            ?.firstOrNull { it.startsWith("etp_rt=") }?.substringAfter("etp_rt=")?.takeIf { it.isNotEmpty() }
        onResult(WebProfileResult(state.selected, state.profiles, state.lines.toList(), cookie.takeIf { it != etpRt }))
    }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else finish()
    }

    DisposableEffect(Unit) {
        cookies.removeAllCookies(null)
        cookies.setCookie(COOKIE_HOST, "etp_rt=$etpRt; Domain=.crunchyroll.com; Path=/; Secure; HttpOnly")
        cookies.flush()
        onDispose {
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
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                cookies.setAcceptCookie(true)
                cookies.setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun report(payload: String) {
                        // Läuft auf dem WebView-Thread; Auswertung und Abschluss auf dem Main-Thread.
                        main.post {
                            if (state.add(payload)) finish()
                        }
                    }
                }, "CrtvBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        view.evaluateJavascript(HOOK_JS, null)
                    }
                    override fun onPageCommitVisible(view: WebView, url: String) {
                        view.evaluateJavascript(HOOK_JS, null)
                    }
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(HOOK_JS, null)
                    }
                }
                isFocusable = true
                isFocusableInTouchMode = true
                loadUrl(PROFILES_URL)
                requestFocus()
                webView = this
            }
        },
        update = { webView = it },
    )
}

/** Wertet die Meldungen der Seite aus. Gibt true zurück, sobald ein Profil gewählt wurde. */
private class WebProfileCollector {
    val lines = mutableListOf<String>()
    var profiles: List<Profile>? = null
    var selected: String? = null
    var done = false
    private val parser = CrunchyrollProfileApi(OkHttpClient())

    fun add(payload: String): Boolean {
        val o = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return false
        val url = o.str("url").orEmpty()
        val path = url.substringAfter("crunchyroll.com", url).substringBefore('?').replace(uuid, "<id>")
        val status = (o["status"] as? JsonPrimitive)?.intOrNull ?: 0
        val headers = (o["headers"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val body = o["body"] as? JsonObject
        val bodyKeys = (body?.get("keys") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

        val parts = mutableListOf("${o.str("method") ?: "?"} $path → $status (${o.str("via")})")
        if (headers.isNotEmpty()) parts += "Header: " + headers.joinToString(" ")
        o.str("credentials")?.let { parts += "credentials=$it" }
        if (bodyKeys.isNotEmpty()) {
            parts += "Form: " + bodyKeys.joinToString(" ") { k ->
                when (k) {
                    "grant_type", "scope" -> "$k=${body?.str(k)}"
                    else -> k
                }
            }
        }
        o.str("auth")?.removePrefix("Bearer ")?.let { parts += "Token: " + ProfileProbe.describeClaims(it) }
        o.str("token")?.let { parts += "neues Token: " + ProfileProbe.describeClaims(it, short = true) }
        lines += parts.joinToString(" · ")

        if (path.endsWith("/multiprofile") && status in 200..299) {
            o.str("response")?.let { r -> runCatching { parser.parse(r) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { profiles = it } }
        }
        // Profilwahl auf der Seite: Token-Request mit profile_id, erfolgreich beantwortet.
        if (path.endsWith("/auth/v1/token") && status in 200..299) {
            val chosen = body?.str("profile_id") ?: o.str("tokenProfile").takeIf { body?.str("profile_id") != null }
            if (chosen != null) {
                selected = chosen
                return true
            }
        }
        return false
    }

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
}
