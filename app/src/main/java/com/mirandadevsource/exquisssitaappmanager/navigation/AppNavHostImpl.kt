package com.mirandadevsource.exquisssitaappmanager.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mirandadevsource.exquisssitaappmanager.features.auth.AuthUiEvent
import com.mirandadevsource.exquisssitaappmanager.features.auth.AuthUiStage
import com.mirandadevsource.exquisssitaappmanager.features.auth.AuthViewModel
import com.mirandadevsource.exquisssitaappmanager.features.auth.AuthLoadingScreen
import com.mirandadevsource.exquisssitaappmanager.features.auth.LoginScreen
import com.mirandadevsource.exquisssitaappmanager.features.main.MainScaffold

@Composable
fun AppNavHost(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    val autofillManager = LocalAutofillManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, uiState.session?.startedAtEpochMillis) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) authViewModel.checkSessionExpiry()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(authViewModel) {
        authViewModel.events.collect { event ->
            if (event is AuthUiEvent.LoginSucceeded) autofillManager?.commit()
        }
    }

    LaunchedEffect(uiState.stage) {
        val currentRoute = navController.currentDestination?.route
        when (uiState.stage) {
            AuthUiStage.CHECKING -> Unit
            AuthUiStage.SIGNED_OUT -> {
                if (currentRoute != null && currentRoute != AppRoutes.LOGIN) {
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            AuthUiStage.SIGNED_IN, AuthUiStage.OFFLINE -> {
                if (currentRoute != AppRoutes.MAIN) {
                    navController.navigate(AppRoutes.MAIN) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoutes.LOGIN,
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    ) {
        composable(AppRoutes.LOGIN) {
            if (uiState.stage == AuthUiStage.CHECKING) {
                AuthLoadingScreen()
            } else {
                LoginScreen(viewModel = authViewModel)
            }
        }
        composable(AppRoutes.MAIN) {
            MainScaffold(
                session = uiState.session,
                onSignOut = authViewModel::signOut,
            )
        }
    }
}
