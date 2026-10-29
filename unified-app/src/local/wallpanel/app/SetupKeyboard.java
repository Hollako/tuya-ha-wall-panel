package local.wallpanel.app;
import android.app.Activity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

/** Settings open with focus outside text fields; editing still opens the keyboard. */
final class SetupKeyboard {
    static void prepare(Activity activity,View page){
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        page.setFocusableInTouchMode(true);page.requestFocus();
    }
    static void hide(Activity activity){
        View focus=activity.getCurrentFocus();
        InputMethodManager keyboard=(InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if(keyboard!=null)keyboard.hideSoftInputFromWindow((focus!=null?focus:activity.getWindow().getDecorView()).getWindowToken(),0);
        View root=activity.findViewById(android.R.id.content);root.setFocusableInTouchMode(true);root.requestFocus();
    }
    private SetupKeyboard(){}
}
