package net.neomenia.glideprototype;

import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.*;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class SpaceConversionTest {
    private KeyboardSession session;
    private String visible="";
    private final List<String> committed=new ArrayList<>();
    @Before public void setup(){
        InputPreferences.get(RuntimeEnvironment.getApplication()).edit().clear()
            .putBoolean("zenzEnabled",false).putBoolean("spaceCyclesCandidates",true).apply();
        session=new KeyboardSession(RuntimeEnvironment.getApplication(),null,null);
        EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;
        session.start(new BaseInputConnection(new View(RuntimeEnvironment.getApplication()),true){
            @Override public boolean setComposingText(CharSequence text,int cursor){visible=text.toString();return true;}
            @Override public boolean commitText(CharSequence text,int cursor){committed.add(text.toString());return true;}
        },info);
    }
    @After public void teardown(){session.destroy();}
    private Object get(String name)throws Exception {Field field=KeyboardSession.class.getDeclaredField(name);field.setAccessible(true);return field.get(session);}
    private void set(String name,Object value)throws Exception {Field field=KeyboardSession.class.getDeclaredField(name);field.setAccessible(true);field.set(session,value);}
    private void ready()throws Exception {
        session.text("hashi",true);
        List<String> choices=List.of("橋","箸","はし");
        set("conversionChoices",choices);set("conversionChoicesReady",true);set("candidateRequest",get("generation"));
        ((CandidateBarView)get("candidates")).setCandidates(choices);
    }
    @Test public void spaceCyclesAndWrapsWithoutCommittingAndEnterCommitsSelection()throws Exception {
        ready();
        for(String expected:List.of("橋","箸","はし","橋")){
            session.action("space");assertEquals(expected,visible);assertTrue(committed.isEmpty());
        }
        session.action("enter");assertEquals(List.of("橋"),committed);
    }
    @Test public void resumedInputDefaultsToCommittingSelectedCandidate()throws Exception {
        ready();session.action("space");session.action("space");session.text("ka",true);
        assertEquals(List.of("箸"),committed);assertEquals("か",visible);
    }
    @Test public void cancellationRestoresReadingAndContinuesComposition()throws Exception {
        InputPreferences.get(RuntimeEnvironment.getApplication()).edit().putBoolean("cancelConversionOnInput",true).apply();
        ready();session.action("space");session.text("ka",true);
        assertTrue(committed.isEmpty());assertEquals("はしか",visible);
        session.action("enter");assertEquals(List.of("はしか"),committed);
    }
    @Test public void latinInputStillCommitsSelectedCandidate()throws Exception {
        InputPreferences.get(RuntimeEnvironment.getApplication()).edit().putBoolean("cancelConversionOnInput",true).apply();
        ready();session.action("space");session.text("x",false);
        assertEquals(List.of("橋","x"),committed);
    }
    @Test public void deleteCancelsSelectionBeforeDeletingReading()throws Exception {
        ready();session.action("space");session.action("delete");assertEquals("はし",visible);
        session.action("delete");assertEquals("は",visible);assertTrue(committed.isEmpty());
    }
    @Test public void disabledCyclingKeepsReadingUnselected()throws Exception {
        ready();InputPreferences.get(RuntimeEnvironment.getApplication()).edit().putBoolean("spaceCyclesCandidates",false).apply();
        session.action("space");assertEquals("はし",visible);assertEquals(-1,get("conversionIndex"));
    }
    @Test public void pendingSpaceDoesNotSelectCandidatesAfterTypingResumes()throws Exception {
        session.text("hashi",true);
        // Keep a request pending to exercise repeated spaces before results arrive.
        set("candidateDisplay",new CandidateDisplayGate(new android.os.Handler(android.os.Looper.getMainLooper()),1000,()->{}));
        session.action("space");session.action("space");assertEquals(2,get("pendingSpaceSteps"));
        session.text("ka",true);assertEquals(0,get("pendingSpaceSteps"));assertEquals("はしか",visible);
    }
    @Test public void selectedCandidateIsHighlightedAndClearedOnConfirmation()throws Exception {
        ready();session.action("space");
        CandidateBarView bar=(CandidateBarView)get("candidates");
        assertEquals(1,selectedCount(bar));session.action("enter");assertEquals(0,selectedCount(bar));
    }
    private int selectedCount(View view){
        int count=view.isSelected()?1:0;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)count+=selectedCount(group.getChildAt(i));
        return count;
    }
}
