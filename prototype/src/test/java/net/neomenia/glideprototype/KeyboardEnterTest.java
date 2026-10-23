package net.neomenia.glideprototype;

import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class KeyboardEnterTest {
    private static final class RecordingConnection extends BaseInputConnection {
        final List<String> calls=new ArrayList<>();
        RecordingConnection(){super(new View(RuntimeEnvironment.getApplication()),true);}
        @Override public boolean performEditorAction(int action){calls.add("action:"+action);return true;}
        @Override public boolean commitText(CharSequence text,int cursor){calls.add("text:"+text+":"+cursor);return true;}
        @Override public boolean sendKeyEvent(KeyEvent event){calls.add("key:"+event.getAction()+":"+event.getKeyCode());return true;}
    }
    private static EditorInfo editor(int options){
        EditorInfo info=new EditorInfo();
        info.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        info.imeOptions=options;
        return info;
    }
    private static void expect(EditorInfo info,String... calls){
        RecordingConnection connection=new RecordingConnection();
        KeyboardSession.enter(connection,info);
        assertEquals(List.of(calls),connection.calls);
    }
    @Test public void noEnterActionOverridesCustomSendAction(){
        EditorInfo info=editor(EditorInfo.IME_ACTION_SEND|EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        info.actionLabel="送信";info.actionId=100;
        expect(info,"text:\n:1");
    }
    @Test public void noEnterActionOverridesStandardActions(){
        for(int action:new int[]{EditorInfo.IME_ACTION_SEND,EditorInfo.IME_ACTION_SEARCH,EditorInfo.IME_ACTION_DONE})
            expect(editor(action|EditorInfo.IME_FLAG_NO_ENTER_ACTION),"text:\n:1");
    }
    @Test public void customActionStillWorksWhenEnterActionIsAllowed(){
        EditorInfo info=editor(EditorInfo.IME_ACTION_SEND);
        info.actionLabel="送信";info.actionId=100;
        expect(info,"action:100");
    }
    @Test public void standardActionsStillWorkWhenAllowed(){
        for(int action:new int[]{EditorInfo.IME_ACTION_SEND,EditorInfo.IME_ACTION_SEARCH,EditorInfo.IME_ACTION_NEXT,EditorInfo.IME_ACTION_DONE})
            expect(editor(action),"action:"+action);
    }
    @Test public void noActionInsertsNewline(){
        expect(editor(EditorInfo.IME_ACTION_NONE),"text:\n:1");
        expect(editor(EditorInfo.IME_ACTION_UNSPECIFIED),"text:\n:1");
        expect(null,"text:\n:1");
    }
    @Test public void rawEditorReceivesEnterKeyEvents(){
        EditorInfo info=editor(EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        info.inputType=InputType.TYPE_NULL;info.actionLabel="送信";info.actionId=100;
        expect(info,"key:"+KeyEvent.ACTION_DOWN+":"+KeyEvent.KEYCODE_ENTER,
                "key:"+KeyEvent.ACTION_UP+":"+KeyEvent.KEYCODE_ENTER);
    }
    @Test public void switchingSendSettingIsReflectedOnTheNextEnter(){
        EditorInfo info=editor(EditorInfo.IME_ACTION_SEND);
        info.actionLabel="送信";info.actionId=100;
        expect(info,"action:100");
        info.imeOptions|=EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        expect(info,"text:\n:1");
        info.imeOptions&=~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        expect(info,"action:100");
    }
}
