package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class OrderedTouchesTest {
    @Test public void earlyCommitRetainsPointerSlotsButDoesNotRepeatOnRelease(){
        OrderedTouches<String> touches=new OrderedTouches<>();touches.start(7,"k");touches.start(12,"a");
        assertTrue(touches.commitHeldPair(7,12));assertFalse(touches.commitHeldPair(7,12));
        assertFalse(touches.start(3,"i"));assertEquals("k",touches.get(7));
        assertTrue(touches.finish(12).isEmpty());assertTrue(touches.start(12,"i"));
        assertEquals(List.of("i"),touches.finish(12));assertTrue(touches.finish(7).isEmpty());
    }
    @Test public void earlyPairCannotDiscardOlderPendingInput(){
        OrderedTouches<String> touches=new OrderedTouches<>();touches.start(7,"k");touches.start(12,"h");
        assertTrue(touches.finish(12).isEmpty());touches.start(12,"a");
        assertFalse(touches.commitHeldPair(7,12));
        assertEquals(List.of("k","h"),touches.finish(7));assertEquals(List.of("a"),touches.finish(12));
    }
    @Test public void overlappingTaProducesKanaInEitherReleaseOrder(){
        for(boolean reverse:new boolean[]{false,true}){
            OrderedTouches<String> touches=new OrderedTouches<>();
            assertTrue(touches.start(7,"t"));assertTrue(touches.start(12,"a"));
            List<String> output=new ArrayList<>();
            output.addAll(touches.finish(reverse?12:7));output.addAll(touches.finish(reverse?7:12));
            assertEquals(List.of("t","a"),output);
            assertEquals("た",Romanizer.convert(String.join("",output),false));
        }
    }
    @Test public void pointerIdsCanBeReusedWhileEarlierTouchRemainsHeld(){
        OrderedTouches<String> touches=new OrderedTouches<>();touches.start(9,"k");touches.start(3,"a");
        assertTrue(touches.finish(3).isEmpty());assertTrue(touches.start(3,"i"));
        assertEquals("k",touches.get(9));assertEquals("i",touches.get(3));
        assertTrue(touches.finish(3).isEmpty());assertEquals(List.of("k","a","i"),touches.finish(9));
    }
    @Test public void cancellationDropsHeldAndCompletedPendingTouches(){
        OrderedTouches<String> touches=new OrderedTouches<>();touches.start(0,"t");touches.start(1,"a");touches.finish(1);touches.clear();
        assertTrue(touches.finish(0).isEmpty());assertTrue(touches.active().isEmpty());
        assertTrue(touches.start(0,"s"));assertEquals(List.of("s"),touches.finish(0));
    }
    @Test public void thirdTouchIsIgnoredWithoutCancellingTheFirstTwo(){
        OrderedTouches<String> touches=new OrderedTouches<>();touches.start(0,"t");touches.start(1,"a");
        assertFalse(touches.start(2,"x"));assertFalse(touches.start(0,"x"));assertNull(touches.get(2));
        assertTrue(touches.finish(2).isEmpty());assertEquals(List.of("t"),touches.finish(0));assertEquals(List.of("a"),touches.finish(1));
    }
}
