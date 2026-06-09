package com.shedule2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        ScheduleWidgetProvider.showCurrentDateInAllWidgets(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ScheduleTheme {
                ScheduleApp()
            }
        }
    }
}
