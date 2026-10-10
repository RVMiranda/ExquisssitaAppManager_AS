package com.mirandadevsource.exquisssitaappmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import com.mirandadevsource.exquisssitaappmanager.features.auth.AuthViewModel
import com.mirandadevsource.exquisssitaappmanager.navigation.AppNavHost
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ExquisssitaAppManagerTheme
import dagger.hilt.android.AndroidEntryPoint

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExquisssitaAppManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val authViewModel: AuthViewModel = hiltViewModel()
                    AppNavHost(authViewModel)
                }
            }
        }
    }
}
