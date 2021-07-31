package com.pascaldornfeld.gsdble.preferences

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.pascaldornfeld.gsdble.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}