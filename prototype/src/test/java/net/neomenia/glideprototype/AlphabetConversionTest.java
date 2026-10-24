package net.neomenia.glideprototype;

import android.text.InputType;
import android.view.*;
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
public class AlphabetConversionTest {
    private KeyboardSession session;
    private final List<String> committed=new ArrayList<>();
    private String visible="";
    @Before public void setup(){
        var context=RuntimeEnvironment.getApplication();
        InputPreferences.get(context).edit().putBoolean("zenzEnabled",false).apply();
        session=new KeyboardSession(context,null,null);
        start(InputType.TYPE_CLASS_TEXT);
    }
    private void start(int inputType){
        EditorInfo info=new EditorInfo();info.inputType=inputType;
        session.start(new BaseInputConnection(new View(RuntimeEnvironment.getApplication()),true){
            @Override public boolean setComposingText(CharSequence text,int cursor){visible=text.toString();return true;}
            @Override public boolean commitText(CharSequence text,int cursor){committed.add(text.toString());return true;}
        },info);
    }
    @After public void teardown(){session.destroy();}
    @Test public void cyclesLowerTitleUpperAndCommitsTheDisplayedAlphabet(){
        session.text("te",true);session.text("su",true);session.text("to",true);assertEquals("てすと",visible);
        for(String expected:new String[]{"tesuto","Tesuto","TESUTO","tesuto","Tesuto"}){
            session.action("alphabet");assertEquals(expected,visible);assertTrue(committed.isEmpty());
        }
        session.action("enter");assertEquals(List.of("Tesuto"),committed);
    }
    @Test public void newJapaneseInputCommitsAlphabetAndStartsANewCycle(){
        session.text("shi",true);session.action("alphabet");assertEquals("shi",visible);
        session.text("ka",true);assertEquals(List.of("shi"),committed);assertEquals("か",visible);
        session.action("alphabet");assertEquals("ka",visible);
    }
    @Test public void deleteAndSpaceOperateOnTheAlphabetWithoutRevertingToKana(){
        session.text("tesuto",true);session.action("alphabet");session.action("alphabet");
        session.action("delete");assertEquals("Tesut",visible);session.action("alphabet");assertEquals("TESUT",visible);
        session.action("space");assertEquals(List.of("TESUT"," "),committed);
    }
    @Test public void deletesBeforeAlphabetConversionDoNotRestoreDeletedLetters(){
        session.text("shi",true);session.text("kya",true);session.action("delete");assertEquals("しき",visible);
        session.action("alphabet");assertEquals("shiki",visible);
    }
    @Test public void emptyCompositionAndNewEditorDoNotReusePreviousInput(){
        session.action("alphabet");assertEquals("",visible);assertTrue(committed.isEmpty());
        session.text("a",true);session.action("alphabet");session.action("delete");assertEquals("",visible);
        session.action("alphabet");assertEquals("",visible);
        session.text("ka",true);session.action("alphabet");session.action("alphabet");start(InputType.TYPE_CLASS_TEXT);
        session.text("te",true);session.action("alphabet");assertEquals("te",visible);
    }
    @Test public void passwordInputDoesNotRetainOrReconvertText(){
        start(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        session.text("ka",true);session.action("alphabet");assertEquals(List.of("ka"),committed);
    }
}
