package net.neomenia.glideprototype;

import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class KeyboardCursorTest {
    private KeyboardSession session;
    private final List<String> calls=new ArrayList<>();
    @Before public void setup(){
        var context=RuntimeEnvironment.getApplication();
        InputPreferences.get(context).edit().clear().putBoolean("zenzEnabled",false).apply();
        session=new KeyboardSession(context,null,null);
        EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;
        session.start(new BaseInputConnection(new View(context),true){
            @Override public boolean setComposingText(CharSequence text,int cursor){return true;}
            @Override public boolean commitText(CharSequence text,int cursor){calls.add("commit:"+text);return true;}
            @Override public boolean finishComposingText(){calls.add("finish");return true;}
            @Override public boolean sendKeyEvent(KeyEvent event){
                calls.add("key:"+event.getAction()+":"+event.getKeyCode());return true;
            }
        },info);
    }
    @After public void teardown(){session.destroy();}
    private void expectKeys(String... actions){
        List<String> expected=new ArrayList<>();
        for(String action:actions){
            session.action(action);
            int code=action.equals("left")?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT;
            expected.add("key:"+KeyEvent.ACTION_DOWN+":"+code);
            expected.add("key:"+KeyEvent.ACTION_UP+":"+code);
        }
        assertEquals(expected,calls);
    }
    @Test public void repeatedCursorMovesDoNotFinishAnEmptyComposition(){
        expectKeys("left","left","right","right","left");
    }
    @Test public void cursorMoveCommitsPendingKanaOnlyOnce(){
        session.text("tesuto",true);session.action("left");
        assertEquals(List.of("commit:てすと","finish","key:0:21","key:1:21"),calls);
        calls.clear();expectKeys("left","right","right");
    }
    @Test public void cursorMoveCommitsDisplayedAlphabetOnlyOnce(){
        session.text("shi",true);session.action("alphabet");session.action("right");
        assertEquals(List.of("commit:shi","finish","key:0:22","key:1:22"),calls);
        calls.clear();expectKeys("right","left");
    }
}
