package com.landxcape.synkromesh.presentation

import android.content.Intent
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
        if (handleExitIntent(intent)) return

        enableEdgeToEdge()
        setContent {
            SynkromeshTheme {
                IntercomRoute()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExitIntent(intent)
    }

    private fun handleExitIntent(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(EXTRA_EXIT, false) == true) {
            finishAffinity()
            return true
        }
        return false
    }

    companion object {
        const val EXTRA_EXIT = "com.landxcape.synkromesh.EXTRA_EXIT"
    }
}
