package net.neomenia.glideprototype;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w400dp-h900dp-mdpi")
public class FloatingKeyboardTest {
    private Context context;
    private SharedPreferences preferences;
    private final List<String> output=new ArrayList<>();
    private KeyboardDockView dock;
    private LinearLayout content;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        preferences=context.getSharedPreferences("floating-test",0);
        preferences.edit().clear().putBoolean("floatingKeyboard",true).apply();
        content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);
        content.addView(new View(context),new LinearLayout.LayoutParams(-1,40));
        GlideKeyboardView keys=new GlideKeyboardView(context,new GlideKeyboardView.Listener(){
            public void text(String raw,boolean japanese){output.add(raw);}
            public void action(String action){}
            public void preview(String route,String value){}
        });
        content.addView(keys,new LinearLayout.LayoutParams(-1,232));
        dock=new KeyboardDockView(context,content,preferences,40,height->{});
        new android.widget.FrameLayout(context).addView(dock);layout();
    }
    private void layout(){
        dock.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.AT_MOST));
        dock.layout(0,0,dock.getMeasuredWidth(),dock.getMeasuredHeight());
    }
    private boolean touch(View target,int action,float x,float y){
        MotionEvent event=MotionEvent.obtain(0,10,action,x,y,0);
        try{return target.dispatchTouchEvent(event);}finally{event.recycle();}
    }
    @Test public void transparentCanvasOnlyAcceptsNewTouchesOnSurface(){
        assertTrue(dock.isFloating());assertEquals(900,dock.getHeight());
        assertEquals(320,content.getWidth());
        assertEquals(Color.TRANSPARENT,((ColorDrawable)dock.getBackground()).getColor());
        assertFalse(dock.touchBounds().contains(10,10));
        assertFalse(touch(dock,MotionEvent.ACTION_DOWN,10,10));
        assertEquals(content.getTop()-28,dock.touchBounds().top);
        assertEquals(content.getBottom(),dock.touchBounds().bottom);
    }
    @Test public void glideCanLeaveSurfaceAndReleaseOutside(){
        float x=content.getLeft()+content.getWidth()/2f,y=content.getTop()+40+29;
        assertTrue(touch(dock,MotionEvent.ACTION_DOWN,x,y));
        assertTrue(touch(dock,MotionEvent.ACTION_MOVE,200,10));
        assertTrue(touch(dock,MotionEvent.ACTION_MOVE,x,y));
        assertTrue(touch(dock,MotionEvent.ACTION_UP,x,-20));
        assertEquals(1,output.size());assertFalse(output.get(0).isEmpty());
    }
    @Test public void cancelledGlideDoesNotCommitOrPoisonNextTouch(){
        float x=content.getLeft()+content.getWidth()/2f,y=content.getTop()+69;
        touch(dock,MotionEvent.ACTION_DOWN,x,y);touch(dock,MotionEvent.ACTION_MOVE,0,10);
        touch(dock,MotionEvent.ACTION_CANCEL,0,10);assertTrue(output.isEmpty());
        touch(dock,MotionEvent.ACTION_DOWN,x,y);touch(dock,MotionEvent.ACTION_UP,x,y);
        assertEquals(1,output.size());
    }
    @Test public void draggingHandlePersistsPositionSeparatelyFromDockedLayout(){
        preferences.edit().putFloat("dockPortrait.width",.9f).apply();
        View handle=dock.getChildAt(3);int top=content.getTop(),left=content.getLeft();
        touch(handle,MotionEvent.ACTION_DOWN,160,14);
        touch(handle,MotionEvent.ACTION_MOVE,180,-86);
        touch(handle,MotionEvent.ACTION_UP,180,-86);layout();
        assertEquals(top-100,content.getTop());assertEquals(left+20,content.getLeft());
        dock.reloadPreferences();layout();assertEquals(top-100,content.getTop());
        dock.setFloating(false);layout();assertEquals(360,content.getWidth());assertEquals(0,content.getTop());
        dock.setFloating(true);layout();assertEquals(top-100,content.getTop());assertEquals(left+20,content.getLeft());
    }
    @Test public void editingExpandsTouchRegionAndKeepsHandleInsideCanvas(){
        dock.edit();layout();assertEquals(dock.getHeight(),dock.touchBounds().bottom);
        assertEquals(0,dock.touchBounds().top);dock.cancelEdit();layout();
        View handle=dock.getChildAt(3);assertEquals(View.VISIBLE,handle.getVisibility());
        assertTrue(handle.getTop()>=0);assertTrue(content.getBottom()<=dock.getHeight());
    }
}
