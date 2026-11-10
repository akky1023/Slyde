package net.neomenia.glideprototype;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;

public final class GlideInputMethodService extends InputMethodService {
    private KeyboardSession session;
    private ImeComposeOwner composeOwner;
    @Override public void onCreate(){
        setTheme(android.R.style.Theme_Material_Light_NoActionBar);super.onCreate();
        composeOwner=new ImeComposeOwner();
        // The window recomposer starts above the input view, at the window content root.
        composeOwner.attach(getWindow().getWindow().getDecorView());
        // A transparent background alone must not leave an opaque backing surface.
        getWindow().getWindow().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        applyWindowColors();
    }
    private void applyWindowColors(){
        android.view.Window window=getWindow().getWindow();
        ImeColors colors=ImeColors.from(this);
        boolean floating=InputPreferences.get(this).getBoolean("floatingKeyboard",false);
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setNavigationBarColor(floating?android.graphics.Color.TRANSPARENT:colors.surface());
        if(android.os.Build.VERSION.SDK_INT>=29)window.setNavigationBarContrastEnforced(false);
        WindowCompat.getInsetsController(window,window.getDecorView()).setAppearanceLightNavigationBars(ColorUtils.calculateLuminance(colors.surface())>.5);
    }
    @Override public View onCreateInputView(){
        if(session!=null)session.destroy();
        getWindow().getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        session=new KeyboardSession(this,null,null);
        composeOwner.attach(session.view());return session.view();
    }
    @Override public boolean onEvaluateFullscreenMode(){return false;}
    @Override public void onComputeInsets(Insets insets){
        super.onComputeInsets(insets);
        if(session==null||!session.isFloating()||!isInputViewShown())return;
        View view=session.view();int[] location=new int[2];view.getLocationInWindow(location);
        // Reserve no editor space. Only presses beginning on the floating surface belong
        // to the IME; Android keeps delivering that gesture when it leaves this region.
        int bottom=getWindow().getWindow().getDecorView().getHeight();
        insets.contentTopInsets=bottom;insets.visibleTopInsets=bottom;
        insets.touchableInsets=Insets.TOUCHABLE_INSETS_REGION;
        android.graphics.Region region=session.touchRegion();region.translate(location[0],location[1]);
        insets.touchableRegion.set(region);
    }
    @Override public boolean onKeyDown(int keyCode,KeyEvent event){
        if(keyCode==KeyEvent.KEYCODE_BACK&&session!=null&&session.cancelLayoutEdit())return true;
        return super.onKeyDown(keyCode,event);
    }
    @Override public void onStartInputView(EditorInfo info,boolean restarting){
        super.onStartInputView(info,restarting);
        applyWindowColors();
        composeOwner.start();
        if(session!=null)session.start(getCurrentInputConnection(),info);
    }
    @Override public void onFinishInputView(boolean finishingInput){
        if(session!=null)session.finish();composeOwner.stop();super.onFinishInputView(finishingInput);
    }
    @Override public void onFinishInput(){
        if(session!=null)session.finish();composeOwner.stop();super.onFinishInput();
    }
    @Override public void onUpdateSelection(int oldStart,int oldEnd,int newStart,int newEnd,int candidatesStart,int candidatesEnd){
        super.onUpdateSelection(oldStart,oldEnd,newStart,newEnd,candidatesStart,candidatesEnd);
        if(session!=null)session.selectionChanged(newStart,newEnd,candidatesEnd);
    }
    @Override public void onDestroy(){if(session!=null)session.destroy();composeOwner.destroy();super.onDestroy();}
}
