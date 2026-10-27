package com.jerry.launcherx.smartspace

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.jerry.launcherx.ui.preferences.PreferenceActivity
import com.jerry.launcherx.ui.preferences.navigation.SmartspaceWidget

class SmartspacePreferencesShortcut : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(PreferenceActivity.createIntent(this, SmartspaceWidget))
        finish()
    }
}
