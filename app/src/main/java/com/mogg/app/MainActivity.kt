package com.mogg.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mogg.app.cardio.CardioRunScreen
import com.mogg.app.data.Slot
import com.mogg.app.data.Store
import com.mogg.app.notify.Reminders
import com.mogg.app.ui.screens.CardioScreen
import com.mogg.app.ui.screens.FaceScreen
import com.mogg.app.ui.screens.GuidesScreen
import com.mogg.app.ui.screens.HomeScreen
import com.mogg.app.ui.screens.NutritionScreen
import com.mogg.app.ui.screens.OnboardingScreen
import com.mogg.app.ui.screens.SettingsScreen
import com.mogg.app.ui.theme.MoggTheme
import com.mogg.app.workout.WorkoutScreen

class MainActivity : ComponentActivity() {
    private val openRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRequest.value = intent?.getStringExtra(Reminders.EXTRA_OPEN)
        setContent { MoggTheme { MoggRoot(openRequest.value) { openRequest.value = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest.value = intent.getStringExtra(Reminders.EXTRA_OPEN)
    }
}

private data class Tab(val route: String, val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Сегодня", Icons.Default.Home),
    Tab("face", "Лицо", Icons.Default.Face),
    Tab("food", "Питание", Icons.Default.Restaurant),
    Tab("cardio", "Кардио", Icons.Default.DirectionsRun),
    Tab("guides", "Гайды", Icons.AutoMirrored.Filled.MenuBook),
)

@Composable
private fun MoggRoot(openRequest: String?, consumed: () -> Unit) {
    val state by Store.state.collectAsState()
    if (!state.profile.onboarded) {
        OnboardingScreen()
        return
    }
    val nav = rememberNavController()
    LaunchedEffect(openRequest) {
        when (openRequest) {
            Reminders.TYPE_MORNING -> nav.navigate("workout/MORNING")
            Reminders.TYPE_EVENING -> nav.navigate("workout/EVENING")
            Reminders.TYPE_CARDIO -> nav.navigate("cardio")
        }
        if (openRequest != null) consumed()
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val fullscreen = route?.startsWith("workout") == true || route == "cardio_run"

    Scaffold(
        bottomBar = {
            if (!fullscreen) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.navigateTab(t.route) },
                        icon = { Icon(t.icon, t.title) },
                        label = { Text(t.title) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = if (fullscreen) Modifier else Modifier.padding(pad)) {
            composable("home") {
                HomeScreen(
                    onWorkout = { nav.navigate("workout/${it.name}") },
                    onCardio = { nav.navigateTab("cardio") },
                    onFood = { nav.navigateTab("food") },
                    onSettings = { nav.navigate("settings") },
                )
            }
            composable("face") { FaceScreen() }
            composable("food") { NutritionScreen() }
            composable("cardio") { CardioScreen(onStart = { nav.navigate("cardio_run") }) }
            composable("cardio_run") { CardioRunScreen(onClose = { nav.popBackStack() }) }
            composable("guides") { GuidesScreen() }
            composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable("workout/{slot}") { entry ->
                val slot = runCatching { Slot.valueOf(entry.arguments?.getString("slot") ?: "MORNING") }.getOrDefault(Slot.MORNING)
                WorkoutScreen(slot, onClose = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo("home") { saveState = true }
    launchSingleTop = true
    restoreState = true
}
