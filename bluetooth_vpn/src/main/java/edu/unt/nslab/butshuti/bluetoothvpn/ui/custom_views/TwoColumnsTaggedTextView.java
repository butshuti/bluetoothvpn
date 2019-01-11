package edu.unt.nslab.butshuti.bluetoothvpn.ui.custom_views;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import edu.unt.nslab.butshuti.bluetoothvpn.R;

/**
 * Created by butshuti on 9/15/18.
 */

public class TwoColumnsTaggedTextView extends LinearLayout {

    private TextView titleTextView, contentTextView;

    public TwoColumnsTaggedTextView(Context context) {
        super(context);
    }

    public TwoColumnsTaggedTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        applyCustomAttributes(context,attrs);
    }

    public TwoColumnsTaggedTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        applyCustomAttributes(context,attrs);
    }

    public TwoColumnsTaggedTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        applyCustomAttributes(context,attrs);
    }

    private void applyCustomAttributes(Context context, AttributeSet attrs) {
        titleTextView = new TextView(context);
        contentTextView = new TextView(context);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.TwoColumnsTaggedTextView,
                0, 0);
        try {
            String title = a.getString(R.styleable.TwoColumnsTaggedTextView_column_title);
            String content = a.getString(R.styleable.TwoColumnsTaggedTextView_column_content);
            titleTextView.setText(title);
            contentTextView.setText(content);
            titleTextView.setTextSize(10);
            titleTextView.setPadding(0, 0, 2, 0);
            setOrientation(HORIZONTAL);
            //setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.app_gradient_background, null));
            addView(titleTextView);
            addView(contentTextView);

        } finally {
            a.recycle();
        }
    }

    public void setText(String txt){
        if(contentTextView != null){
            contentTextView.setText(txt);
        }
    }
}
