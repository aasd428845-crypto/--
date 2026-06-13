package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request Notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 101)
        }

        setContent {
            MyApplicationTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                val prefs = remember { context.getSharedPreferences("nawaem_prefs", android.content.Context.MODE_PRIVATE) }

                var currentUserRole by remember {
                    val savedRole = prefs.getString("current_role", null)
                    val outRole = if (savedRole != null) {
                        try { UserRole.valueOf(savedRole) } catch (e: Exception) { null }
                    } else { null }
                    mutableStateOf(outRole)
                }
                var currentUserName by remember {
                    mutableStateOf(prefs.getString("current_user_name", "") ?: "")
                }
                var currentScreen by remember {
                    val savedScreen = prefs.getString("current_screen", null)
                    val outScreen = if (savedScreen != null) {
                        try { AppScreen.valueOf(savedScreen) } catch (e: Exception) { AppScreen.LOGIN }
                    } else { AppScreen.LOGIN }
                    mutableStateOf(outScreen)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            AppScreen.LOGIN -> LoginScreen(
                                onLoginSuccess = { role, userName ->
                                    currentUserRole = role
                                    currentUserName = userName
                                    val nextScreen = if (role == UserRole.CEO) AppScreen.CEO_DASHBOARD else AppScreen.SUPERVISOR_DASHBOARD
                                    currentScreen = nextScreen
                                    
                                    // Save session persistently
                                    prefs.edit().apply {
                                        putString("current_role", role.name)
                                        putString("current_user_name", userName)
                                        putString("current_screen", nextScreen.name)
                                        apply()
                                    }
                                },
                                onNavigateToSql = {
                                    currentScreen = AppScreen.SQL_GUIDE
                                }
                            )
                            AppScreen.CEO_DASHBOARD -> CeoDashboardScreen(
                                currentUserName = currentUserName,
                                onLogout = {
                                    currentUserRole = null
                                    currentUserName = ""
                                    currentScreen = AppScreen.LOGIN
                                    // Clear persisted session
                                    prefs.edit().clear().apply()
                                },
                                onNavigateToSql = {
                                    currentScreen = AppScreen.SQL_GUIDE
                                }
                            )
                            AppScreen.SUPERVISOR_DASHBOARD -> SupervisorDashboardScreen(
                                currentSupervisorName = currentUserName,
                                onLogout = {
                                    currentUserRole = null
                                    currentUserName = ""
                                    currentScreen = AppScreen.LOGIN
                                    // Clear persisted session
                                    prefs.edit().clear().apply()
                                }
                            )
                            AppScreen.SQL_GUIDE -> SqlGuideScreen(
                                onBack = {
                                    currentScreen = if (currentUserRole == null) {
                                        AppScreen.LOGIN
                                    } else if (currentUserRole == UserRole.CEO) {
                                        AppScreen.CEO_DASHBOARD
                                    } else {
                                        AppScreen.SUPERVISOR_DASHBOARD
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
