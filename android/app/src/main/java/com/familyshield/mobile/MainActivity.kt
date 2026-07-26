package com.familyshield.mobile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

class MainActivity : ComponentActivity() {
    private val pendingChatDestination = mutableStateOf<ChatPushDestination?>(null)

    // Apply the saved language (English/Hebrew) before any resources are resolved.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Locales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        Configuration.getInstance().userAgentValue = packageName
        setContent {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingChatDestination.value = chatPushDestinationFromIntent(intent)
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
