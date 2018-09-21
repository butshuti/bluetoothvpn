package edu.unt.nsllab.butshuti.bluetoothvpn.ui;

import android.support.v4.app.Fragment;

import edu.unt.nsllab.butshuti.bluetoothvpn.ui.fragments.PingTestResultsFragment;
import edu.unt.nsllab.butshuti.bluetoothvpn.ui.templates.ToolbarFrameActivity;

public class TracerouteActivity extends ToolbarFrameActivity {

    @Override
    public Fragment getMainFragment() {
        return new PingTestResultsFragment();
    }
}
