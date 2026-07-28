package com.familyshield.mobile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.fragment.app.FragmentActivity
import com.familyshield.mobile.kid.KidApp
import com.familyshield.mobile.net.PrefsTokenStore
import com.familyshield.mobile.parent.ParentApp
import com.familyshield.mobile.push.ChatPushDestination
import com.familyshield.mobile.push.chatPushDestinationFromIntent
import com.familyshield.mobile.push.ensureChatPushNotificationChannel
import com.familyshield.mobile.push.ensureSafetyAlertNotificationChannel
import com.familyshield.mobile.push.ensureUrgentPushNotificationChannel
import com.familyshield.mobile.ui.theme.FamilyShieldTheme
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : FragmentActivity() {
    private val pendingChatDestination = mutableStateOf<ChatPushDestination?>(null)
    private var attachedLanguageTag = ""

    // Apply the saved language (English/Hebrew) before any resources are resolved.
    override fun attachBaseContext(newBase: Context) {
        attachedLanguageTag = Locales.saved(newBase)
        super.attachBaseContext(Locales.wrap(newBase, attachedLanguageTag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locales.initialize(this)
        pendingChatDestination.value = chatPushDestinationFromIntent(intent)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 4201)
        }
        ensureChatPushNotificationChannel(this)
        ensureSafetyAlertNotificationChannel(this)
        ensureUrgentPushNotificationChannel(this)
        configureOpenStreetMap()
        setContent {
            val languageTag = Locales.currentTag
            LaunchedEffect(languageTag) {
                if (localeChangeRequiresActivityRecreation(attachedLanguageTag, languageTag)) {
                    recreate()
                }
            }
            val localizedContext = remember(languageTag) { Locales.wrap(this@MainActivity, languageTag) }
            val layoutDirection = if (Locales.isRtl(Locales.resolveLocale(this@MainActivity, languageTag))) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                LocalLayoutDirection provides layoutDirection,
            ) {
                FamilyShieldTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Root(
                            chatDestination = pendingChatDestination.value,
                            onChatDestinationConsumed = { consumed ->
                                if (pendingChatDestination.value == consumed) pendingChatDestination.value = null
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingChatDestination.value = chatPushDestinationFromIntent(intent)
    }

    private fun configureOpenStreetMap() {
        val prefs = getSharedPreferences(OSMDROID_PREFS, MODE_PRIVATE)
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles").apply { mkdirs() }
            setCacheMapTileCount(MAP_MEMORY_TILE_COUNT)
            setCacheMapTileOvershoot(MAP_MEMORY_TILE_OVERSHOOT)
            setTileFileSystemCacheMaxBytes(MAP_TILE_CACHE_MAX_BYTES)
            setTileFileSystemCacheTrimBytes(MAP_TILE_CACHE_TRIM_BYTES)
        }
    }

    private companion object {
        private const val OSMDROID_PREFS = "osmdroid"
        private const val MAP_MEMORY_TILE_COUNT: Short = 64
        private const val MAP_MEMORY_TILE_OVERSHOOT: Short = 1
        private const val MAP_TILE_CACHE_MAX_BYTES = 150L * 1024L * 1024L
        private const val MAP_TILE_CACHE_TRIM_BYTES = 100L * 1024L * 1024L
    }
}

@Composable
private fun Root(
    chatDestination: ChatPushDestination?,
    onChatDestinationConsumed: (ChatPushDestination) -> Unit,
) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val store = remember(context) { PrefsTokenStore(context.applicationContext) }
    var kidPaired by remember { mutableStateOf(store.deviceToken != null) }
    LaunchedEffect(chatDestination?.key, kidPaired) {
        when (chatDestination?.recipient) {
            "child" -> nav.navigate("kid") { launchSingleTop = true }
            "parent" -> nav.navigate("parent") { launchSingleTop = true }
        }
    }
    // A paired kid device owns this install until all linked parents are unpaired.
    // Otherwise the app opens in the parent login/dashboard flow.
    NavHost(navController = nav, startDestination = if (kidPaired) "kid" else "parent") {
        composable("parent") {
            ParentApp(
                onKidDevice = { nav.navigate("kid") },
                chatDestination = chatDestination?.takeIf { it.recipient == "parent" },
                onChatDestinationConsumed = onChatDestinationConsumed,
            )
        }
        composable("kid") {
            KidApp(
                onBack = { nav.popBackStack() },
                chatDestination = chatDestination?.takeIf { it.recipient == "child" },
                onChatDestinationConsumed = onChatDestinationConsumed,
                onKidPaired = {
                    kidPaired = true
                },
                onKidUnpaired = {
                    kidPaired = false
                    nav.navigate("parent") {
                        popUpTo("kid") { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
