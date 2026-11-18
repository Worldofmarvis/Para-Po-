package com.parapo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.parapo.ui.ParaPoApp
import com.parapo.ui.theme.ParaPoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParaPoTheme {
                ParaPoApp()
            }
        }
    }
}
