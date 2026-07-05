package com.familyshield.app

import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familyshield.app.kid.KidApp
import com.familyshield.app.net.PrefsTokenStore
import com.familyshield.app.parent.ParentApp
import com.familyshield.app.ui.theme.FamilyShieldTheme
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    // Apply the saved language (English/Hebrew) before any resources are resolved.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Locales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 4201)
        }
        Configuration.getInstance().userAgentValue = packageName
        setContent {
            FamilyShieldTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root()
                }
            }
        }
    }
}

@Composable
private fun Root() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val store = remember(context) { PrefsTokenStore(context.applicationContext) }
    var kidPaired by remember { mutableStateOf(store.deviceToken != null) }
    // A paired kid device owns this install until all linked parents are unpaired.
    // Otherwise the app opens in the parent login/dashboard flow.
    NavHost(navController = nav, startDestination = if (kidPaired) "kid" else "parent") {
        composable("parent") {
            ParentApp(
                onKidDevice = { nav.navigate("kid") },
            )
        }
        composable("kid") {
            KidApp(
                onBack = { nav.popBackStack() },
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
