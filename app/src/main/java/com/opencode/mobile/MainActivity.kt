package com.opencode.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.opencode.mobile.data.AppViewModel
import com.opencode.mobile.data.Screen
import com.opencode.mobile.ui.screens.*
import com.opencode.mobile.ui.theme.Oc
import com.opencode.mobile.ui.theme.OpenCodeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OpenCodeTheme(vm.themeMode) { OpenCodeApp(vm) } }
    }
}

@Composable
fun OpenCodeApp(vm: AppViewModel) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val open: () -> Unit = { scope.launch { drawer.open() } }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = vm.stack.size > 1) { vm.pop() }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = vm.top == Screen.Main,
        drawerContent = { DrawerContent(vm) { scope.launch { drawer.close() } } },
    ) {
        AnimatedContent(
            targetState = vm.top,
            modifier = Modifier.fillMaxSize().background(Oc.c.bg),
            transitionSpec = {
                if (targetState.depth >= initialState.depth)
                    (slideInHorizontally(tween(320)) { it } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(320)))
                else
                    (slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(320)) { it } + fadeOut(tween(320)))
            },
            label = "nav"
        ) { screen ->
            when (screen) {
                Screen.Main -> Main(vm, open)
                Screen.Settings -> SettingsHub(vm)
                Screen.Appearance -> AppearanceScreen(vm)
                Screen.Language -> LanguageScreen(vm)
                Screen.Sessions -> SessionsScreen(vm)
                Screen.WhatsNew -> WhatsNewScreen(vm)
                Screen.Experimental -> ExperimentalScreen(vm)
                Screen.Voice -> VoiceScreen(vm)
                Screen.Servers -> ServersScreen(vm)
            }
        }
    }
}

@Composable
private fun Main(vm: AppViewModel, onMenu: () -> Unit) {
    AnimatedContent(
        targetState = vm.currentSession,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
        label = "main"
    ) { id -> if (id == null) HomeScreen(vm, onMenu) else ChatScreen(vm, onMenu) }
}
