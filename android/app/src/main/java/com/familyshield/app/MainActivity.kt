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
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familyshield.app.kid.KidApp
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
        val locationPerms = buildList {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        if (locationPerms.isNotEmpty()) requestPermissions(locationPerms.toTypedArray(), 4202)
        if (Build.VERSION.SDK_INT >= 29 &&
            checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION), 4203)
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
    // The app's home is the parent experience (login → dashboard). The kid-device
    // setup is reachable from the login screen for pairing this phone.
    NavHost(navController = nav, startDestination = "parent") {
        composable("parent") { ParentApp(onKidDevice = { nav.navigate("kid") }) }
        composable("kid") { KidApp(onBack = { nav.popBackStack() }) }
    }
}
