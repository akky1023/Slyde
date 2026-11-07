package net.neomenia.glideprototype;

import android.text.InputType;
import android.text.Selection;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class KeyboardDeleteTest {
    private KeyboardSession session;
    private RecordingConnection connection;
    private EditorInfo info;
    private static final class RecordingConnection extends BaseInputConnection {
        final List<String> calls=new ArrayList<>();
        boolean surroundingAvailable=true,standardSupported=true,codePointsSupported=true;
        RecordingConnection(){super(new View(RuntimeEnvironment.getApplication()),true);}
        @Override public CharSequence getSelectedText(int flags){
            CharSequence selected=super.getSelectedText(flags);
            return selected==null?"":selected;
        }
        @Override public CharSequence getTextBeforeCursor(int length,int flags){
            return surroundingAvailable?super.getTextBeforeCursor(length,flags):null;
        }
        @Override public boolean commitText(CharSequence text,int cursor){
            calls.add("commit:"+text);return super.commitText(text,cursor);
        }
        @Override public boolean deleteSurroundingText(int before,int after){
            calls.add("delete:"+before+":"+after);
            return standardSupported&&super.deleteSurroundingText(before,after);
        }
        @Override public boolean deleteSurroundingTextInCodePoints(int before,int after){
            calls.add("codePoints:"+before+":"+after);
            // Model an editor that acknowledges this call without deleting anything.
            return codePointsSupported;
        }
        @Override public boolean sendKeyEvent(KeyEvent event){
            calls.add("key:"+event.getAction()+":"+event.getKeyCode());return true;
        }
    }
    @Before public void setup(){
        var context=RuntimeEnvironment.getApplication();
        InputPreferences.get(context).edit().clear().putBoolean("zenzEnabled",false).apply();
        session=new KeyboardSession(context,null,null);
        connection=new RecordingConnection();info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;
        session.start(connection,info);
    }
    @After public void teardown(){session.destroy();}
    private void text(String text){
        connection.getEditable().replace(0,connection.getEditable().length(),text);
        Selection.setSelection(connection.getEditable(),text.length());
    }
    private void expectDelete(String before,String after,int count){
        text(before);session.action("delete");
        assertEquals(after,connection.getEditable().toString());
        assertEquals(List.of("delete:"+count+":0"),connection.calls);
    }
    @Test public void emptySelectionDoesNotSwallowDelete(){expectDelete("abc","ab",1);}
    @Test public void kanaUsesStandardDeletion(){expectDelete("あいう","あい",1);}
    @Test public void emojiDeletesBothSurrogates(){expectDelete("a😀","a",2);}
    @Test public void cursorInMiddlePreservesFollowingText(){
        text("a😀b");Selection.setSelection(connection.getEditable(),3);session.action("delete");
        assertEquals("ab",connection.getEditable().toString());
        assertEquals(List.of("delete:2:0"),connection.calls);
    }
    @Test public void selectedTextIsDeleted(){
        text("abcd");Selection.setSelection(connection.getEditable(),1,3);session.action("delete");
        assertEquals("ad",connection.getEditable().toString());
        assertEquals(List.of("commit:"),connection.calls);
    }
    @Test public void startOfTextDoesNotDeleteFollowingText(){
        text("abc");Selection.setSelection(connection.getEditable(),0);session.action("delete");
        assertEquals("abc",connection.getEditable().toString());
        assertEquals(List.of("delete:1:0"),connection.calls);
    }
    @Test public void unsupportedStandardDeletionFallsBackToCodePoints(){
        text("a");connection.standardSupported=false;session.action("delete");
        assertEquals(List.of("delete:1:0","codePoints:1:0"),connection.calls);
    }
    @Test public void unavailableSurroundingTextUsesCodePoints(){
        connection.surroundingAvailable=false;session.action("delete");
        assertEquals(List.of("codePoints:1:0"),connection.calls);
    }
    @Test public void unsupportedDeletionFallsBackToKeyEvents(){
        connection.standardSupported=false;connection.codePointsSupported=false;session.action("delete");
        assertEquals(List.of("delete:1:0","codePoints:1:0","key:0:67","key:1:67"),connection.calls);
    }
    @Test public void rawEditorReceivesOnlyDeleteKeyEvents(){
        info.inputType=InputType.TYPE_NULL;session.start(connection,info);session.action("delete");
        assertEquals(List.of("key:0:67","key:1:67"),connection.calls);
    }
    @Test public void pendingKanaIsEditedBeforeCommittedText(){
        text("abc");session.text("ai",true);connection.calls.clear();session.action("delete");
        assertEquals("abcあ",connection.getEditable().toString());
        assertTrue(connection.calls.isEmpty());
    }
    @Test public void repeatedDeleteRemovesOneCodePointAtATime(){
        text("a😀b");session.action("delete");session.action("delete");session.action("delete");
        assertEquals("",connection.getEditable().toString());
        assertEquals(List.of("delete:1:0","delete:2:0","delete:1:0"),connection.calls);
    }
}
