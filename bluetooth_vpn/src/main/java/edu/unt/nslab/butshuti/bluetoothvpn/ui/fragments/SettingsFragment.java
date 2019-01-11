package edu.unt.nslab.butshuti.bluetoothvpn.ui.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;

import edu.unt.nslab.butshuti.bluetoothvpn.R;

public final class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {


    private SharedPreferences settings;

    public SettingsFragment(){

    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings);
        settings = PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        SharedPreferences.Editor editor = settings.edit();
        if(key.equals(getString(R.string.pref_key_enable_passive_mode))){
            editor = editBoolean(editor, sharedPreferences, key);
        }else if(key.equals(getString(R.string.pref_key_enable_client_mode))){
            editor = editBoolean(editor, sharedPreferences, key);
        }else if(key.equals(getString(R.string.pref_key_num_active_connections))){
            editor = editString(editor, sharedPreferences, key, "-1");
        }else if(key.equals(getString(R.string.pref_key_install_default_routes))){
            editor = editBoolean(editor, sharedPreferences, key);
        }else if(key.equals(getString(R.string.pref_key_routing_mode))){
            editor = editString(editor, sharedPreferences, key, "UNKNOWN");
        }
        editor.apply();
    }

    private SharedPreferences.Editor editBoolean(SharedPreferences.Editor editor, SharedPreferences pref, String key){
        Boolean prefValue = pref.getBoolean(key, false);
        return editor.putBoolean(key, prefValue);
    }

    private SharedPreferences.Editor editInt(SharedPreferences.Editor editor, SharedPreferences pref, String key, int defaultValue){
        int prefValue = pref.getInt(key, defaultValue);
        return editor.putInt(key, prefValue);
    }

    private SharedPreferences.Editor editString(SharedPreferences.Editor editor, SharedPreferences pref, String key, String defaultValue){
        String prefValue = pref.getString(key, defaultValue);
        return editor.putString(key, prefValue);
    }
}
