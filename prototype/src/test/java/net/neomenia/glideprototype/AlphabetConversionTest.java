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
        InputPreferences.get(context).edit().clear().putBoolean("zenzEnabled",false).apply();
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
    @Test public void alphabetConvertsLongVowelMarkToHyphenInEveryCase(){
        for(String raw:new String[]{"j","ー","po","p"})session.text(raw,true);
        assertEquals("jーぽp",visible);
        for(String expected:new String[]{"j-pop","J-pop","J-POP"}){
            session.action("alphabet");assertEquals(expected,visible);
        }
        session.action("enter");assertEquals(List.of("J-POP"),committed);
    }
    @Test public void enabledFullWidthAlphabetStillFormsKana(){
        InputPreferences.get(RuntimeEnvironment.getApplication()).edit().putBoolean("fullWidthComposingAlphabet",true).apply();
        session.text("shi",true);session.text("k",true);assertEquals("しｋ",visible);
        session.text("y",true);assertEquals("しｋｙ",visible);
        session.action("delete");assertEquals("しｋ",visible);
        session.text("a",true);assertEquals("しか",visible);
        session.action("enter");assertEquals(List.of("しか"),committed);
    }
    @Test public void pendingAlphabetWidthCanBeChangedWithoutChangingTheReading(){
        var preferences=InputPreferences.get(RuntimeEnvironment.getApplication());
        session.text("k",true);assertEquals("k",visible);
        preferences.edit().putBoolean("fullWidthComposingAlphabet",true).apply();session.reloadSettings();
        assertEquals("ｋ",visible);
        preferences.edit().putBoolean("fullWidthComposingAlphabet",false).apply();session.reloadSettings();
        assertEquals("k",visible);
        session.action("enter");assertEquals(List.of("k"),committed);
    }
    @Test public void fullWidthDisplayDoesNotChangeExplicitAlphabetOrLatinInput(){
        InputPreferences.get(RuntimeEnvironment.getApplication()).edit().putBoolean("fullWidthComposingAlphabet",true).apply();
        session.text("ka",true);session.text("k",true);assertEquals("かｋ",visible);
        session.action("alphabet");assertEquals("kak",visible);
        session.reloadSettings();assertEquals("kak",visible);
        session.action("enter");session.text("ABC123",false);
        assertEquals(List.of("kak","ABC123"),committed);
    }
    @Test public void fullWidthDisplayLeavesNumbersSymbolsAndSupplementaryCharactersAlone(){
        InputPreferences.get(RuntimeEnvironment.getApplication()).edit().putBoolean("fullWidthComposingAlphabet",true).apply();
        session.text("k1!😀",true);assertEquals("ｋ1!😀",visible);
        session.action("delete");assertEquals("ｋ1!",visible);
        session.action("delete");session.action("delete");assertEquals("ｋ",visible);
        session.text("a",true);assertEquals("か",visible);
    }
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
    @Test public void separateConsonantAfterAlphabetSurvivesEditorSelectionUpdates(){
        class Editor extends BaseInputConnection {
            int batchDepth;
            final List<int[]> updates=new ArrayList<>();
            Editor(){super(new View(RuntimeEnvironment.getApplication()),true);}
            private void changed(){
                var text=getEditable();
                int[] update={android.text.Selection.getSelectionStart(text),android.text.Selection.getSelectionEnd(text),getComposingSpanEnd(text)};
                if(batchDepth>0)updates.clear();
                updates.add(update);
            }
            @Override public boolean beginBatchEdit(){batchDepth++;return true;}
            @Override public boolean endBatchEdit(){batchDepth--;return true;}
            @Override public boolean commitText(CharSequence text,int cursor){boolean result=super.commitText(text,cursor);changed();return result;}
            @Override public boolean setComposingText(CharSequence text,int cursor){boolean result=super.setComposingText(text,cursor);changed();return result;}
            @Override public boolean finishComposingText(){boolean result=super.finishComposingText();changed();return result;}
            void deliver(){
                while(!updates.isEmpty()){
                    var pending=new ArrayList<>(updates);updates.clear();
                    for(int[] update:pending)session.selectionChanged(update[0],update[1],update[2]);
                }
            }
        }
        Editor editor=new Editor();EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;
        session.start(editor,info);editor.deliver();
        for(String raw:new String[]{"a","k","a","s","a"}){session.text(raw,true);editor.deliver();}
        session.action("alphabet");editor.deliver();assertEquals("akasa",editor.getEditable().toString());
        for(String raw:new String[]{"t","a","n","a","h","a"}){session.text(raw,true);editor.deliver();}
        session.action("enter");editor.deliver();assertEquals("akasaたなは",editor.getEditable().toString());
    }
    private void cancelOnInput(){
        InputPreferences.get(RuntimeEnvironment.getApplication()).edit().putBoolean("cancelAlphabetOnInput",true).apply();
    }
    @Test public void disabledButtonKeepsKanaAndCanBeReenabled(){
        var preferences=InputPreferences.get(RuntimeEnvironment.getApplication());
        preferences.edit().putBoolean("alphabetButtonEnabled",false).apply();
        session.text("shi",true);session.action("alphabet");assertEquals("し",visible);
        session.text("ka",true);assertEquals("しか",visible);assertTrue(committed.isEmpty());
        preferences.edit().putBoolean("alphabetButtonEnabled",true).apply();
        session.action("alphabet");assertEquals("shika",visible);
    }
    @Test public void enabledModeRestoresKanaInEveryAlphabetCaseAndContinuesComposition(){
        cancelOnInput();
        for(int taps=1;taps<=3;taps++){
            start(InputType.TYPE_CLASS_TEXT);
            session.text("shi",true);for(int i=0;i<taps;i++)session.action("alphabet");
            session.text("ka",true);assertEquals("しか",visible);assertTrue(committed.isEmpty());
            session.action("alphabet");assertEquals("shika",visible);
        }
        session.text("ku",true);session.action("enter");assertEquals(List.of("しかく"),committed);
    }
    @Test public void resumedInputPreservesConfirmedNAndRespectsAlphabetDeletes(){
        cancelOnInput();
        session.text("n",true);session.text("n",true);session.text("a",true);
        session.action("alphabet");session.text("ka",true);assertEquals("んあか",visible);
        start(InputType.TYPE_CLASS_TEXT);
        session.text("shika",true);session.action("alphabet");session.action("delete");assertEquals("shik",visible);
        session.text("u",true);assertEquals("しく",visible);assertTrue(committed.isEmpty());
    }
    @Test public void enabledModeStillCommitsAlphabetOnExplicitConfirmationOrLatinInput(){
        cancelOnInput();
        session.text("shi",true);session.action("alphabet");session.action("enter");
        session.text("ka",true);session.action("alphabet");session.text("x",false);
        assertEquals(List.of("shi","ka","x"),committed);
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
