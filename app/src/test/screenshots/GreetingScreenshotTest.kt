package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.screens.*
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        LoginScreen(onLoginSuccess = { _, _ -> }, onNavigateToSql = {})
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }

  @Test
  fun ceo_dashboard_render() {
    composeTestRule.setContent {
      MyApplicationTheme {
        CeoDashboardScreen(
          currentUserName = "المدير التنفيذي",
          onLogout = {},
          onNavigateToSql = {}
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun supervisor_dashboard_render() {
    composeTestRule.setContent {
      MyApplicationTheme {
        SupervisorDashboardScreen(
          currentSupervisorName = "ماهر الحربي",
          onLogout = {}
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun main_activity_render() {
    composeTestRule.setContent {
      MyApplicationTheme {
        var currentScreen by remember { mutableStateOf(AppScreen.LOGIN) }
        var currentUserRole by remember { mutableStateOf<UserRole?>(null) }
        var currentUserName by remember { mutableStateOf("") }

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
                            currentScreen = if (role == UserRole.CEO) AppScreen.CEO_DASHBOARD else AppScreen.SUPERVISOR_DASHBOARD
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
    composeTestRule.waitForIdle()
  }
}
