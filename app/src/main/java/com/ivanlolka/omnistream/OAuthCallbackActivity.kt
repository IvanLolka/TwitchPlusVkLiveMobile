package com.ivanlolka.omnistream

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import com.ivanlolka.omnistream.auth.OAuthProvider

class OAuthCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val provider = detectProvider(data) ?: run {
            finish()
            return
        }

        val redirect = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_PROCESS_OAUTH_CALLBACK
            putExtra(MainActivity.EXTRA_OAUTH_PROVIDER, provider.name)
            putExtra(MainActivity.EXTRA_OAUTH_CALLBACK_URI, data.toString())
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(redirect)
        finish()
    }

    private fun detectProvider(uri: Uri?): OAuthProvider? {
        return when (uri?.lastPathSegment?.lowercase()) {
            "twitch" -> OAuthProvider.TWITCH
            "vk" -> OAuthProvider.VK
            else -> null
        }
    }
}
