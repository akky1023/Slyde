package net.neomenia.glideprototype;

import android.content.Context;
import android.os.Looper;
import android.view.*;
import android.widget.FrameLayout;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ImeSettingsInteractionTest {
    private final List<String> events=new ArrayList<>();
    private GlideKeyboardView keyboard(){
        Context context=RuntimeEnvironment.getApplication();
        GlideKeyboardView keyboard=new GlideKeyboardView(context,new GlideKeyboardView.Listener(){
            public void text(String raw,boolean japanese){events.add("text:"+raw);}
            public void action(String action){events.add(action);}
            public void preview(String route,String result){}
        });
        FrameLayout parent=new FrameLayout(context);parent.addView(keyboard);
        parent.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY));
        parent.layout(0,0,400,240);return keyboard;
    }
    private void touch(GlideKeyboardView keyboard,int action,float x,float y){
        MotionEvent event=MotionEvent.obtain(0,android.os.SystemClock.uptimeMillis(),action,x,y,0);
        keyboard.onTouchEvent(event);event.recycle();
    }
    private void hold(){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout()+1));}
    @Test public void shortTapRequestsAlphabetAndLongPressOpensSettingsOnlyOnce(){
        GlideKeyboardView keyboard=keyboard();
        touch(keyboard,MotionEvent.ACTION_DOWN,30,30);touch(keyboard,MotionEvent.ACTION_UP,30,30);hold();
        assertEquals(List.of("alphabet"),events);events.clear();
        touch(keyboard,MotionEvent.ACTION_DOWN,30,30);hold();assertEquals(List.of("settings"),events);
        hold();touch(keyboard,MotionEvent.ACTION_UP,30,30);assertEquals(List.of("settings"),events);
    }
    @Test public void draggingOrCancellingSettingsDoesNotOpenOrType(){
        GlideKeyboardView keyboard=keyboard();
        touch(keyboard,MotionEvent.ACTION_DOWN,30,30);touch(keyboard,MotionEvent.ACTION_MOVE,200,30);hold();
        touch(keyboard,MotionEvent.ACTION_UP,200,30);assertTrue(events.isEmpty());
        touch(keyboard,MotionEvent.ACTION_DOWN,30,30);touch(keyboard,MotionEvent.ACTION_CANCEL,30,30);hold();
        assertTrue(events.isEmpty());
    }
    @Test public void numberLayoutOffersSettingsAndLatinShiftStillWorks(){
        GlideKeyboardView keyboard=keyboard();keyboard.showNumbers();
        touch(keyboard,MotionEvent.ACTION_DOWN,30,30);hold();touch(keyboard,MotionEvent.ACTION_UP,30,30);
        assertEquals(List.of("settings"),events);events.clear();keyboard.resetMode(true);
        touch(keyboard,MotionEvent.ACTION_DOWN,30,30);hold();touch(keyboard,MotionEvent.ACTION_UP,30,30);
        assertTrue(events.isEmpty());
        touch(keyboard,MotionEvent.ACTION_DOWN,100,30);touch(keyboard,MotionEvent.ACTION_UP,100,30);
        assertEquals(List.of("text:A"),events);
    }
    @Test public void modeToggleUsesBothFormerBottomSidebarRows(){
        GlideKeyboardView keyboard=keyboard();
        // Both halves of the enlarged button switch modes, rather than requesting alphabet conversion.
        touch(keyboard,MotionEvent.ACTION_DOWN,30,125);touch(keyboard,MotionEvent.ACTION_UP,30,125);
        assertFalse(keyboard.isJapanese());assertEquals(List.of("commit"),events);events.clear();
        touch(keyboard,MotionEvent.ACTION_DOWN,30,235);touch(keyboard,MotionEvent.ACTION_UP,30,235);
        assertTrue(keyboard.isJapanese());assertEquals(List.of("commit"),events);events.clear();
        keyboard.showNumbers();
        touch(keyboard,MotionEvent.ACTION_DOWN,30,150);touch(keyboard,MotionEvent.ACTION_UP,30,150);
        assertTrue(keyboard.isJapanese());assertEquals(List.of("commit"),events);
    }
    private <T> T find(View view,Class<T> type){
        if(type.isInstance(view))return type.cast(view);
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){
            T found=find(group.getChildAt(i),type);if(found!=null)return found;
        }
        return null;
    }
    private void layout(View root){
        root.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.AT_MOST));
        root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());
    }
    @Test public void settingsReplaceEntireKeyboardAndBackRestoresItsSize(){
        Context context=RuntimeEnvironment.getApplication();
        InputPreferences.get(context).edit().putBoolean("zenzEnabled",false).apply();
        KeyboardSession session=new KeyboardSession(context,null,null);
        try{
            View root=session.view();layout(root);
            GlideKeyboardView keyboard=find(root,GlideKeyboardView.class);
            CandidateBarView candidates=find(root,CandidateBarView.class);
            ImeSettingsMenu menu=find(root,ImeSettingsMenu.class);
            int height=root.getHeight(),keysHeight=keyboard.getHeight(),candidateHeight=candidates.getHeight();
            touch(keyboard,MotionEvent.ACTION_DOWN,20,20);hold();layout(root);
            assertEquals(View.VISIBLE,menu.getVisibility());assertEquals(View.GONE,((View)keyboard.getParent()).getVisibility());assertEquals(View.GONE,candidates.getVisibility());
            assertEquals(keysHeight+candidateHeight,menu.getHeight());assertEquals(height,root.getHeight());
            menu.getChildAt(0).performClick();layout(root);
            assertEquals(View.GONE,menu.getVisibility());assertEquals(View.VISIBLE,((View)keyboard.getParent()).getVisibility());assertEquals(View.VISIBLE,candidates.getVisibility());
            assertEquals(keysHeight,keyboard.getHeight());assertEquals(height,root.getHeight());
            session.action("settings");assertTrue(session.cancelLayoutEdit());layout(root);
            assertEquals(View.GONE,menu.getVisibility());
            session.action("settings");
            android.widget.ScrollView scroll=(android.widget.ScrollView)menu.getChildAt(1);
            ViewGroup items=(ViewGroup)scroll.getChildAt(0);items.getChildAt(0).performClick();
            assertEquals(View.GONE,menu.getVisibility());assertTrue(((KeyboardDockView)root).isEditing());
            session.cancelLayoutEdit();session.action("settings");items.getChildAt(1).performClick();
            android.content.Intent intent=shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
            assertEquals(SettingsActivity.class.getName(),intent.getComponent().getClassName());
        }finally{session.destroy();}
    }
}
