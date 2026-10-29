package com.miu.fold.dashboard

import com.miu.fold.dashboard.shortcuts.PackageNameRule
import com.miu.fold.dashboard.shortcuts.UrlCheck
import com.miu.fold.dashboard.shortcuts.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {
    private fun ok(s: String) = UrlValidator.check(s) as? UrlCheck.Ok
    private fun rejected(s: String) = UrlValidator.check(s) is UrlCheck.Rejected

    @Test fun acceptsHttpsAndAddsScheme() {
        assertEquals("https://claude.ai", ok("https://claude.ai")?.url)
        assertEquals("https://chatgpt.com/", ok("chatgpt.com/")?.url)
        assertEquals("https://example.com:8443/a", ok("example.com:8443/a")?.url)
        assertTrue(ok("http://example.com")!!.insecure)
        assertFalse(ok("https://example.com/search?q=fold")!!.insecure)
    }

    @Test fun t15_rejectsDangerousSchemes() {
        listOf(
            "javascript:alert(1)", "JAVASCRIPT:alert(1)", "intent://scan/#Intent;scheme=zxing;end",
            "file:///sdcard/a.txt", "content://com.x/1", "data:text/html,<script>", "ftp://x.com",
            "market://details?id=x", "tel:123", "sms:1", "about:blank",
        ).forEach { assertTrue(it, rejected(it)) }
    }

    @Test fun rejectsSecretsAndUserInfo() {
        assertTrue(rejected("https://api.example.com/?api_key=abc"))
        assertTrue(rejected("https://x.com/cb?access_token=abc"))
        assertTrue(rejected("https://x.com/?Token=1"))
        assertTrue(rejected("https://user:pass@x.com/"))
        assertTrue(rejected("https://x.com/a b"))
        assertTrue(rejected(""))
        assertTrue(rejected("https://"))
        assertTrue(rejected("https://x.com/" + "a".repeat(3000)))
    }

    @Test fun packageNames() {
        assertTrue(PackageNameRule.isValid("com.anthropic.claude"))
        assertFalse(PackageNameRule.isValid("com"))
        assertFalse(PackageNameRule.isValid("com.x;rm -rf"))
        assertFalse(PackageNameRule.isValid("../etc"))
    }
}
