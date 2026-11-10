package com.landxcape.synkromesh.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.landxcape.synkromesh.presentation.intercom.IntercomRoute
import com.landxcape.synkromesh.presentation.theme.SynkromeshTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SynkromeshTheme {
                IntercomRoute()
            }
        }
    }
}
