package com.mirandadevsource.exquisssitaappmanager.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirandadevsource.exquisssitaappmanager.features.auth.AuthLoadingScreen
import com.mirandadevsource.exquisssitaappmanager.features.auth.AuthUiEvent
import com.mirandadevsource.exquisssitaappmanager.features.auth.AuthUiStage
import com.mirandadevsource.exquisssitaappmanager.features.auth.AuthViewModel
import com.mirandadevsource.exquisssitaappmanager.features.auth.LoginScreen
import com.mirandadevsource.exquisssitaappmanager.features.main.MainScaffold

/**
 * Shell de navegación raíz con aislamiento estricto de sesión (F01 / SEC-01).
 *
 * Separa de forma absoluta el ámbito autenticado de la pantalla de login:
 * - Cuando la sesión se cierra (o expira), el grafo autenticado y sus ViewModels
 *   son destruidos inmediatamente mediante [key].
 * - [LoginScreen] se presenta como única raíz composable; el botón Atrás no tiene
 *   ningún destino previo al cual regresar y cierra la aplicación.
 * - [MainScaffold] nunca se renderiza si la sesión es nula.
 */
@Composable
fun AppNavHost(authViewModel: AuthViewModel) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
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

    when (uiState.stage) {
        AuthUiStage.CHECKING -> {
            AuthLoadingScreen()
        }
        AuthUiStage.SIGNED_OUT -> {
            LoginScreen(viewModel = authViewModel)
        }
        AuthUiStage.SIGNED_IN, AuthUiStage.OFFLINE -> {
            val session = uiState.session
            if (session != null) {
                key(session.userId, session.startedAtEpochMillis) {
                    MainScaffold(
                        session = session,
                        onSignOut = authViewModel::signOut,
                    )
                }
            } else {
                LoginScreen(viewModel = authViewModel)
            }
        }
    }
}
