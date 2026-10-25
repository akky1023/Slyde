package net.neomenia.glideprototype;

import android.view.MotionEvent;
import android.widget.FrameLayout;
import java.util.*;
import net.neomenia.inputcore.Romanizer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class MultitouchInputTest {
    private final List<String> output=new ArrayList<>();
    private final GlideKeyboardView view=new GlideKeyboardView(RuntimeEnvironment.getApplication(),new GlideKeyboardView.Listener(){
        public void text(String raw,boolean japanese){output.add(raw);}
        public void action(String action){}
        public void preview(String route,String result){}
    });
    private final long downTime=android.os.SystemClock.uptimeMillis();
    private void setup(){
        FrameLayout parent=new FrameLayout(RuntimeEnvironment.getApplication());parent.addView(view);view.layout(0,0,1000,400);
    }
    // Pointers retain non-sequential IDs to exercise Android pointer-index handling.
    private void event(int action,int[] ids,float... xy){
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[ids.length];
        for(int i=0;i<ids.length;i++){
            properties[i]=new MotionEvent.PointerProperties();properties[i].id=ids[i];properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords();coords[i].x=xy[2*i];coords[i].y=xy[2*i+1];coords[i].pressure=1;coords[i].size=1;
        }
        MotionEvent event=MotionEvent.obtain(downTime,android.os.SystemClock.uptimeMillis(),action,ids.length,properties,coords,0,0,1,1,0,0,0,0);
        view.onTouchEvent(event);event.recycle();
    }
    @Test public void consonantVowelIsEnteredOnSecondDownForEveryBaseKeyAndVowel(){
        setup();
        int[] consonants={1,2,3,4,5,7,8},vowels={0,6,9,10,11};
        for(int consonant:consonants)for(int vowel:vowels)for(boolean reverse:new boolean[]{false,true}){
            output.clear();float cx=267+233*(consonant%3),cy=50+100*(consonant/3),vx=267+233*(vowel%3),vy=50+100*(vowel/3);
            event(MotionEvent.ACTION_DOWN,new int[]{7},cx,cy);assertTrue(output.isEmpty());
            event(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{7,12},cx,cy,vx,vy);
            String raw=net.neomenia.inputcore.KeyLayout.KEYS[consonant]+net.neomenia.inputcore.KeyLayout.KEYS[vowel];
            assertEquals(List.of(raw),output);
            event(MotionEvent.ACTION_MOVE,new int[]{7,12},733,350,500,50);
            event(MotionEvent.ACTION_POINTER_UP|((reverse?1:0)<<8),new int[]{7,12},733,350,500,50);
            event(MotionEvent.ACTION_UP,new int[]{reverse?7:12},267,50);
            assertEquals(List.of(raw),output);
        }
    }
    @Test public void heldCommittedFingerDoesNotBlockLaterTapOrRepeatConsonant(){
        setup();event(MotionEvent.ACTION_DOWN,new int[]{7},500,50);
        event(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{7,12},500,50,267,50);
        assertEquals(List.of("ka"),output);
        event(MotionEvent.ACTION_POINTER_UP|(1<<8),new int[]{7,12},500,50,267,50);
        event(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{7,12},500,50,267,250);
        assertEquals(List.of("ka"),output);
        event(MotionEvent.ACTION_POINTER_UP|(1<<8),new int[]{7,12},500,50,267,250);
        assertEquals(List.of("ka","i"),output);
        event(MotionEvent.ACTION_UP,new int[]{7},500,50);
        assertEquals(List.of("ka","i"),output);
    }
    @Test public void otherPairsAndModesKeepReleaseOrderBehavior(){
        setup();
        for(int mode=0;mode<4;mode++){
            output.clear();view.resetMode(mode==2);if(mode==3)view.showNumbers();
            float firstX=mode==0?267:500,secondX=mode==0?500:mode==1?733:267;
            event(MotionEvent.ACTION_DOWN,new int[]{7},firstX,50);
            event(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{7,12},firstX,50,secondX,50);
            assertTrue(output.isEmpty());
            event(MotionEvent.ACTION_POINTER_UP|(1<<8),new int[]{7,12},firstX,50,secondX,50);assertTrue(output.isEmpty());
            event(MotionEvent.ACTION_UP,new int[]{7},firstX,50);
            assertEquals(mode==0?List.of("a","k"):mode==1?List.of("k","h"):mode==2?List.of("k","a"):List.of("2","1"),output);
        }
    }
    @Test public void cancelAfterImmediateInputDoesNotEmitAgain(){
        setup();event(MotionEvent.ACTION_DOWN,new int[]{7},733,150);
        event(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{7,12},733,150,267,50);
        assertEquals("た",Romanizer.convert(String.join("",output),false));
        event(MotionEvent.ACTION_CANCEL,new int[]{7,12},733,150,267,50);
        event(MotionEvent.ACTION_DOWN,new int[]{7},267,250);event(MotionEvent.ACTION_UP,new int[]{7},267,250);
        assertEquals(List.of("ta","i"),output);
    }
    @Test public void completedGlideIsNotMistakenForHeldConsonant(){
        setup();event(MotionEvent.ACTION_DOWN,new int[]{7},500,50);
        event(MotionEvent.ACTION_MOVE,new int[]{7},267,50);
        event(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{7,12},267,50,267,250);
        assertTrue(output.isEmpty());
        event(MotionEvent.ACTION_POINTER_UP|(1<<8),new int[]{7,12},267,50,267,250);
        event(MotionEvent.ACTION_UP,new int[]{7},267,50);
        assertEquals(List.of("ka","i"),output);
    }
    @Test public void interveningReleasedTouchIsNotDroppedByLaterVowel(){
        setup();event(MotionEvent.ACTION_DOWN,new int[]{7},500,50);
        event(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{7,12},500,50,733,50);
        event(MotionEvent.ACTION_POINTER_UP|(1<<8),new int[]{7,12},500,50,733,50);
        event(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{7,12},500,50,267,50);
        assertTrue(output.isEmpty());
        event(MotionEvent.ACTION_POINTER_UP|(1<<8),new int[]{7,12},500,50,267,50);
        event(MotionEvent.ACTION_UP,new int[]{7},500,50);
        assertEquals(List.of("k","h","a"),output);
    }
}
