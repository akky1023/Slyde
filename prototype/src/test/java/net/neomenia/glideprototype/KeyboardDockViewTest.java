package net.neomenia.glideprototype;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w400dp-h900dp-mdpi")
public class KeyboardDockViewTest {
    private void layout(KeyboardDockView dock){
        dock.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.AT_MOST));
        dock.layout(0,0,dock.getMeasuredWidth(),dock.getMeasuredHeight());
    }
    private void drag(View handles,float x,float y,float dx,float dy){
        long time=android.os.SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){
            MotionEvent event=MotionEvent.obtain(time,time,action,x+(action==MotionEvent.ACTION_DOWN?0:dx),y+(action==MotionEvent.ACTION_DOWN?0:dy),0);
            handles.dispatchTouchEvent(event);event.recycle();
        }
    }
    private KeyboardDockView create(SharedPreferences prefs,int candidateHeight){
        Context context=RuntimeEnvironment.getApplication();
        LinearLayout content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);
        content.addView(new View(context),new LinearLayout.LayoutParams(-1,candidateHeight));
        content.addView(new View(context),new LinearLayout.LayoutParams(-1,0,1));
        KeyboardDockView dock=new KeyboardDockView(context,content,prefs,candidateHeight,height->{});
        new android.widget.FrameLayout(context).addView(dock);
        dock.edit();layout(dock);return dock;
    }
    @Test public void resizeFrameStartsAtKeysAndCandidateStripFollowsTopEdge(){
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("frame",0);
        KeyboardDockView dock=create(prefs,40);
        LinearLayout content=(LinearLayout)dock.getChildAt(0);View keys=content.getChildAt(1),handles=dock.getChildAt(1);
        int bodyTop=content.getTop()+keys.getTop(),bottom=content.getBottom(),height=keys.getHeight();
        assertEquals(232,height);
        drag(handles,200,bodyTop-handles.getTop(),0,-40);layout(dock);
        assertEquals(height+40,keys.getHeight());assertEquals(bottom,content.getBottom());
        assertEquals(bodyTop-40,content.getTop()+keys.getTop());
        assertEquals(40,keys.getTop());
    }
    @Test public void savedBodyHeightSurvivesCandidateHeightChangeAndReload(){
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("saved",0);
        KeyboardDockView dock=create(prefs,40);LinearLayout content=(LinearLayout)dock.getChildAt(0);
        View handles=dock.getChildAt(1);
        drag(handles,200,content.getTop()+40-handles.getTop(),0,20);layout(dock);
        android.widget.LinearLayout actions=(android.widget.LinearLayout)((android.widget.LinearLayout)dock.getChildAt(2)).getChildAt(2);
        actions.getChildAt(3).performClick();layout(dock);
        assertEquals(212,prefs.getFloat("dockPortrait.keyHeight",0),0);
        content.getChildAt(0).getLayoutParams().height=80;dock.setCandidateHeight(80);layout(dock);
        assertEquals(212,content.getChildAt(1).getHeight());
        dock.reloadPreferences();layout(dock);assertEquals(212,content.getChildAt(1).getHeight());
        assertEquals(80,content.getChildAt(1).getTop());
    }
    @Test public void oldCombinedHeightIsMigratedOnce(){
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("legacy",0);
        prefs.edit().putFloat("dockPortrait.height",300).apply();
        KeyboardDockView dock=create(prefs,40);assertEquals(300,dock.getChildAt(0).getHeight());
        dock.cancelEdit();dock.setCandidateHeight(80);dock.reloadPreferences();layout(dock);
        assertEquals(340,dock.getChildAt(0).getHeight());
    }
}
