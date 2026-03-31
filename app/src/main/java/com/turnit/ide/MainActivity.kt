package com.turnit.ide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.turnit.ide.ui.IdeTheme
import com.turnit.ide.ui.SetupScreen

class MainActivity : ComponentActivity() {
    
    // Initialize our C++ Bridge
    private val nativeBridge = NativeBridge()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Test the C++ connection immediately
        val engineStatus = nativeBridge.stringFromJNI()
        println("TurnIt-IDE C++ Status: $engineStatus")

        setContent {
            IdeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Booting directly into Claude's Phase 1 Setup UI
                    SetupScreen()
                }
            }
        }
    }
}
