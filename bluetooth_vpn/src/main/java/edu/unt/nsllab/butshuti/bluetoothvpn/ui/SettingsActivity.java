package edu.unt.nsllab.butshuti.bluetoothvpn.ui;


import android.support.v4.app.Fragment;

import edu.unt.nsllab.butshuti.bluetoothvpn.ui.fragments.SettingsFragment;
import edu.unt.nsllab.butshuti.bluetoothvpn.ui.templates.ToolbarFrameActivity;

public class SettingsActivity extends ToolbarFrameActivity {

    @Override
    public Fragment getMainFragment() {
        return new SettingsFragment();
    }
}
