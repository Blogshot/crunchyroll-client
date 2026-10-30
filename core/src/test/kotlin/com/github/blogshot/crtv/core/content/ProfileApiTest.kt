package com.github.blogshot.crtv.core.content

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileApiTest {
    @Test
    fun `Profile werden mit Avatar-URL gelesen`() {
        val body = """{"profiles":[
            {"profile_id":"p1","profile_name":"Sascha","avatar":"chibi.png","is_primary":true,"is_selected":true},
            {"profile_id":"p2","profile_name":"Gast","avatar":"https://cdn/x.png","is_primary":false},
            {"profile_name":"ohne ID"}
        ]}"""
        val profiles = CrunchyrollProfileApi(OkHttpClient()).parse(body)
        assertEquals(listOf("p1", "p2"), profiles.map { it.id })
        assertEquals("https://static.crunchyroll.com/assets/avatar/170x170/chibi.png", profiles[0].avatarUrl)
        assertEquals("https://cdn/x.png", profiles[1].avatarUrl)
        assertEquals(true, profiles[0].isPrimary)
    }

    @Test
    fun `Diagnose zeigt Claim-Namen, aber keine Konto-Werte`() {
        val payload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"geheim-123","scope":"account content","profile_id":"p-geheim","exp":1}""".toByteArray())
        val text = ProfileProbe.describeClaims("x.$payload.y")
        org.junit.Assert.assertTrue(text, text.contains("scope=account content"))
        org.junit.Assert.assertTrue(text, text.contains("profile_id"))
        org.junit.Assert.assertFalse(text, text.contains("geheim"))
    }
}
