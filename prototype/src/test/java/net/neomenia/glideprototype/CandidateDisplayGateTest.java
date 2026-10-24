package net.neomenia.glideprototype;

import android.os.Handler;
import android.os.Looper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class CandidateDisplayGateTest {
    private final List<String> shown=new ArrayList<>();
    private String value="辞書";
    private CandidateDisplayGate gate(boolean wait){return gate(wait?20:0);}
    private CandidateDisplayGate gate(int ms){return new CandidateDisplayGate(new Handler(Looper.getMainLooper()),ms,()->shown.add(value));}
    private static void advance(int ms){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}

    @Test public void dictionaryWaitsAtMostTwentyMilliseconds(){
        CandidateDisplayGate gate=gate(true);gate.update();advance(19);assertTrue(shown.isEmpty());
        advance(1);assertEquals(List.of("辞書"),shown);
        value="AI";gate.update();assertEquals(List.of("辞書","AI"),shown);
    }
    @Test public void aiCompletionPublishesCombinedResultsBeforeDeadline(){
        CandidateDisplayGate gate=gate(true);gate.update();advance(5);
        value="辞書とAI";gate.update();gate.release();assertEquals(List.of("辞書とAI"),shown);
        advance(20);assertEquals(1,shown.size());
    }
    @Test public void aiWithoutCandidateAlsoReleasesDictionary(){
        CandidateDisplayGate gate=gate(true);gate.update();advance(3);gate.release();
        assertEquals(List.of("辞書"),shown);advance(20);assertEquals(1,shown.size());
    }
    @Test public void lateDictionaryDoesNotRestartDeadline(){
        CandidateDisplayGate gate=gate(true);advance(15);value="新しい辞書候補";gate.update();
        advance(5);assertEquals(List.of("新しい辞書候補"),shown);
    }
    @Test public void disabledWaitingPublishesImmediately(){
        CandidateDisplayGate gate=gate(false);gate.update();assertEquals(List.of("辞書"),shown);
        gate.release();advance(20);assertEquals(1,shown.size());
    }
    @Test public void cancelledRequestCannotPublishStaleResults(){
        CandidateDisplayGate gate=gate(true);gate.update();gate.cancel();advance(20);
        gate.release();gate.update();assertTrue(shown.isEmpty());
    }
    @Test public void variableWaitCountsReadingCodePoints(){
        assertEquals(40,CandidateDisplayGate.waitMillis(true,true,"あ".repeat(10)));
        assertEquals(24,CandidateDisplayGate.waitMillis(true,true,"あ😀"));
        assertEquals(20,CandidateDisplayGate.waitMillis(true,false,"あ".repeat(10)));
        assertEquals(0,CandidateDisplayGate.waitMillis(false,true,"あ".repeat(10)));
    }
    @Test public void variableTimeoutUsesCalculatedDeadline(){
        CandidateDisplayGate gate=gate(CandidateDisplayGate.waitMillis(true,true,"あ".repeat(10)));
        gate.update();advance(39);assertTrue(shown.isEmpty());advance(1);
        assertEquals(List.of("辞書"),shown);
    }
    @Test public void variableWaitAlsoEndsAsSoonAsAiFinishes(){
        CandidateDisplayGate gate=gate(40);gate.update();advance(7);value="AI";
        gate.update();gate.release();assertEquals(List.of("AI"),shown);
        advance(40);assertEquals(1,shown.size());
    }
}
