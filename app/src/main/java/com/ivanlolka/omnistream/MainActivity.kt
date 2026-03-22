package com.ivanlolka.omnistream

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ivanlolka.omnistream.auth.OAuthProvider
import com.ivanlolka.omnistream.ui.SettingsFragment
import com.ivanlolka.omnistream.ui.StreamsFragment
import com.ivanlolka.omnistream.ui.WatchFragment

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var streamsFragment: Fragment
    private lateinit var watchFragment: Fragment
    private lateinit var settingsFragment: Fragment
    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        val manager = supportFragmentManager
        streamsFragment = manager.findFragmentByTag(TAG_FEED) ?: StreamsFragment()
        watchFragment = manager.findFragmentByTag(TAG_WATCH) ?: WatchFragment()
        settingsFragment = manager.findFragmentByTag(TAG_SETTINGS) ?: SettingsFragment()

        if (savedInstanceState == null) {
            manager.commit {
                add(R.id.contentContainer, streamsFragment, TAG_FEED)
                add(R.id.contentContainer, watchFragment, TAG_WATCH)
                hide(watchFragment)
                add(R.id.contentContainer, settingsFragment, TAG_SETTINGS)
                hide(settingsFragment)
            }
        }

        bottomNavigation = findViewById(R.id.bottomNavigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_feed -> showFragment(streamsFragment)
                R.id.menu_watch -> showFragment(watchFragment)
                R.id.menu_settings -> showFragment(settingsFragment)
            }
            true
        }
        bottomNavigation.selectedItemId = R.id.menu_feed

        handleOAuthIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        if (intent?.action != ACTION_PROCESS_OAUTH_CALLBACK) return
        val providerName = intent.getStringExtra(EXTRA_OAUTH_PROVIDER) ?: return
        val callbackUri = intent.getStringExtra(EXTRA_OAUTH_CALLBACK_URI) ?: return
        val provider = runCatching { OAuthProvider.valueOf(providerName) }.getOrNull() ?: return
        viewModel.processOAuthCallback(provider, callbackUri)
        intent.action = null
        intent.removeExtra(EXTRA_OAUTH_PROVIDER)
        intent.removeExtra(EXTRA_OAUTH_CALLBACK_URI)
    }

    private fun showFragment(target: Fragment) {
        supportFragmentManager.commit {
            listOf(streamsFragment, watchFragment, settingsFragment).forEach { fragment ->
                if (fragment == target) show(fragment) else hide(fragment)
            }
        }
    }

    fun openWatchTab() {
        bottomNavigation.selectedItemId = R.id.menu_watch
    }

    companion object {
        const val ACTION_PROCESS_OAUTH_CALLBACK = "com.ivanlolka.omnistream.action.PROCESS_OAUTH_CALLBACK"
        const val EXTRA_OAUTH_PROVIDER = "extra_oauth_provider"
        const val EXTRA_OAUTH_CALLBACK_URI = "extra_oauth_callback_uri"

        private const val TAG_FEED = "feed_fragment"
        private const val TAG_WATCH = "watch_fragment"
        private const val TAG_SETTINGS = "settings_fragment"
    }
}
