package edu.unt.nsllab.butshuti.bluetoothvpn.ui.custom_views;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.EditText;

import edu.unt.nsllab.butshuti.bluetoothvpn.R;

import static android.view.KeyEvent.KEYCODE_ENTER;

public class CustomSearchView extends LinearLayoutCompat {

    public CustomSearchView(Context context) {
        super(context);
    }

    public CustomSearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomSearchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void registerSearchHandler(SearchViewListener listener){
        Button button = findViewById(R.id.search_button);
        EditText searchText = findViewById(R.id.search_text);
        if(button != null && searchText != null){
            SearchqueryValidator handler = new SearchqueryValidator() {
                @Override
                public void handleSearchQuery() {
                    String query = searchText.getText().toString();
                    if(!listener.onQuerySubmitted(query)){
                        searchText.setTextColor(Color.RED);
                    }
                }
            };
            int textColor = searchText.getCurrentTextColor();
            searchText.setOnKeyListener((v, keyCode, event) -> {
                searchText.setTextColor(textColor);
                return false;
            });
            searchText.setImeActionLabel("SUBMIT", KEYCODE_ENTER);
            searchText.setOnEditorActionListener((v, actionId, event) -> {
                if(event != null){
                    handler.handleSearchQuery();
                    return true;
                }
                return false;
            });
            button.setOnClickListener(v -> {
                handler.handleSearchQuery();
            });
        }
    }

    public void setQueryText(String text){
        EditText searchText = findViewById(R.id.search_text);
        if(searchText != null){
            searchText.setText(text);
        }
    }

    public interface SearchViewListener{
        boolean onQuerySubmitted(String query);
        void refreshLastQuery();
    }

    private interface SearchqueryValidator{
        void handleSearchQuery();
    }

}
