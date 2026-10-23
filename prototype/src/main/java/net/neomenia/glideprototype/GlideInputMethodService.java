package net.neomenia.glideprototype;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

public final class GlideInputMethodService extends InputMethodService {
    private KeyboardSession session;
    @Override public void onCreate(){setTheme(android.R.style.Theme_Material_Light_NoActionBar);super.onCreate();}
    @Override public View onCreateInputView(){
        if(session!=null)session.destroy();
        session=new KeyboardSession(this,null,null);return session.view();
    }
    @Override public boolean onEvaluateFullscreenMode(){return false;}
    @Override public boolean onKeyDown(int keyCode,KeyEvent event){
        if(keyCode==KeyEvent.KEYCODE_BACK&&session!=null&&session.cancelLayoutEdit())return true;
        return super.onKeyDown(keyCode,event);
    }
    @Override public void onStartInputView(EditorInfo info,boolean restarting){
        super.onStartInputView(info,restarting);
        if(session!=null)session.start(getCurrentInputConnection(),info);
    }
    @Override public void onFinishInputView(boolean finishingInput){
        if(session!=null)session.finish();super.onFinishInputView(finishingInput);
    }
    @Override public void onFinishInput(){
        if(session!=null)session.finish();super.onFinishInput();
    }
    @Override public void onUpdateSelection(int oldStart,int oldEnd,int newStart,int newEnd,int candidatesStart,int candidatesEnd){
        super.onUpdateSelection(oldStart,oldEnd,newStart,newEnd,candidatesStart,candidatesEnd);
        if(session!=null)session.selectionChanged(newStart,newEnd,candidatesEnd);
    }
    @Override public void onDestroy(){if(session!=null)session.destroy();super.onDestroy();}
}
