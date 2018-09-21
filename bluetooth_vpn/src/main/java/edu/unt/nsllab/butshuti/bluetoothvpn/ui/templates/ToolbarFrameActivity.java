package edu.unt.nsllab.butshuti.bluetoothvpn.ui.templates;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import edu.unt.nsllab.butshuti.bluetoothvpn.R;
import edu.unt.nsllab.butshuti.bluetoothvpn.ui.custom_views.CustomSearchView;


public abstract class ToolbarFrameActivity extends AppCompatActivity {

    private QuerySaver querySaver = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.default_screen);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportFragmentManager().beginTransaction().replace(R.id.frame, getMainFragment()).commit();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    protected void enableSearchInterface(CustomSearchView.SearchViewListener searchViewListener){
        CustomSearchView searchView = findViewById(R.id.search_view);
        FloatingActionButton refreshFab = findViewById(R.id.refresh_fab);
        searchView.setVisibility(View.VISIBLE);
        refreshFab.setVisibility(View.VISIBLE);
        searchView.registerSearchHandler(searchViewListener);
        refreshFab.setOnClickListener(view -> searchViewListener.refreshLastQuery());
        querySaver = query -> searchView.setQueryText(query);
    }

    protected void saveQuery(String query){
        if(query != null && querySaver != null){
            querySaver.saveQuery(query);
        }
    }

    public abstract Fragment getMainFragment();

    private interface QuerySaver{
        void saveQuery(String query);
    }

}
