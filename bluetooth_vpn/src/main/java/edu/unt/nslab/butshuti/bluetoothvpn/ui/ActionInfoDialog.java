package edu.unt.nslab.butshuti.bluetoothvpn.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;

/**
 * This is a utility class for displaying dialogs to warn or confirm actions.
 * @author butshuti
 */

public class ActionInfoDialog extends AlertDialog.Builder{

    private ActionInfoDialog(Context context) {
        super(context);
    }

    private ActionInfoDialog(Context context, int themeResId) {
        super(context, themeResId);
    }

    public static ActionInfoDialog fromContext(Context ctx){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            return new ActionInfoDialog(ctx, android.R.style.Theme_Material_Light_Dialog_Alert);
        }else{
            return new ActionInfoDialog(ctx);
        }
    }

    public void setMessage(String title, String msg){
        setTitle(title);
        setMessage(msg);
    }

    public void configureActions(DialogInterface.OnClickListener positiveButtonListener, DialogInterface.OnClickListener negativeButtonListener){
        setPositiveButton("Continue", positiveButtonListener);
        setNegativeButton("Dismiss", negativeButtonListener);
        setIcon(android.R.drawable.ic_dialog_alert);
    }
}
