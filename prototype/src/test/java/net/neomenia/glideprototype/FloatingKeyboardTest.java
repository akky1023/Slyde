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
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
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
    private void showResizeHandles(){
        View bar=dock.getChildAt(3);float x=(bar.getLeft()+bar.getRight())/2f,y=(bar.getTop()+bar.getBottom())/2f;
        touch(dock,MotionEvent.ACTION_DOWN,x,y);assertEquals(View.VISIBLE,dock.getChildAt(4).getVisibility());
        touch(dock,MotionEvent.ACTION_UP,x,y);
    }
    @Test public void tappingBottomBarShowsHandlesAndTheyExpireThreeSecondsAfterRelease(){
        showResizeHandles();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2999));
        assertEquals(View.VISIBLE,dock.getChildAt(4).getVisibility());
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1));
        assertEquals(View.GONE,dock.getChildAt(4).getVisibility());
    }
    @Test public void everyCornerResizesImmediatelyAndSavesOnlyFloatingSize(){
        preferences.edit().putFloat("dockPortrait.width",.9f).putFloat("dockPortrait.keyHeight",210).apply();
        for(int corner=0;corner<4;corner++){
            preferences.edit().putFloat("floatingPortrait.width",.8f).putFloat("floatingPortrait.keyHeight",232).putFloat("floatingPortrait.bottom",24).putFloat("floatingPortrait.left",.5f).apply();
            dock.reloadPreferences();layout();showResizeHandles();
            boolean left=corner%2==0,top=corner<2;
            int fixedX=left?content.getRight():content.getLeft(),fixedY=top?dock.getChildAt(3).getBottom():content.getTop();
            float x=left?content.getLeft()-8:content.getRight()+8,y=top?content.getTop()-8:dock.getChildAt(3).getBottom()+8;
            assertFalse(dock.touchBounds().contains((int)x,(int)y));assertTrue(dock.touchRegion().contains((int)x,(int)y));
            touch(dock,MotionEvent.ACTION_DOWN,x,y);
            touch(dock,MotionEvent.ACTION_MOVE,x+(left?40:-40),y+(top?40:-40));layout();
            assertEquals(280,content.getWidth());assertEquals(232,content.getHeight());
            assertEquals(fixedX,left?content.getRight():content.getLeft());assertEquals(fixedY,top?dock.getChildAt(3).getBottom():content.getTop());
            // A held handle must not disappear even when the finger pauses.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(4));
            assertEquals(View.VISIBLE,dock.getChildAt(4).getVisibility());
            touch(dock,MotionEvent.ACTION_UP,x+(left?40:-40),y+(top?40:-40));
            assertEquals(.7f,preferences.getFloat("floatingPortrait.width",0),.001f);
            assertEquals(192,preferences.getFloat("floatingPortrait.keyHeight",0),0);
            assertEquals(.9f,preferences.getFloat("dockPortrait.width",0),0);
            assertEquals(210,preferences.getFloat("dockPortrait.keyHeight",0),0);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2999));
            assertEquals(View.VISIBLE,dock.getChildAt(4).getVisibility());
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1));
            assertEquals(View.GONE,dock.getChildAt(4).getVisibility());
        }
    }
    @Test public void cancelledResizeRestoresBoundsAndVisibleHandlesDoNotBlockGliding(){
        showResizeHandles();int width=content.getWidth(),height=content.getHeight();
        float x=content.getLeft()+8,y=content.getTop()+8;
        touch(dock,MotionEvent.ACTION_DOWN,x,y);touch(dock,MotionEvent.ACTION_MOVE,x+40,y+40);layout();
        touch(dock,MotionEvent.ACTION_CANCEL,x+40,y+40);layout();
        assertEquals(width,content.getWidth());assertEquals(height,content.getHeight());
        glideCanLeaveSurfaceAndReleaseOutside();
        dock.setFloating(false);layout();assertEquals(View.GONE,dock.getChildAt(4).getVisibility());
    }
    @Test public void outsideHandleTouchRegionsExpireWithoutBlockingBackgroundAlongEdges(){
        int x=content.getLeft()-8,y=content.getTop()-8;
        assertFalse(dock.touchRegion().contains(x,y));showResizeHandles();
        assertTrue(dock.touchRegion().contains(x,y));
        assertFalse(dock.touchRegion().contains(content.getLeft()-8,(content.getTop()+content.getBottom())/2));
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3));
        assertFalse(dock.touchRegion().contains(x,y));
    }
    @Test public void beginningInputImmediatelyHidesHandlesWithoutInterruptingTheGlide(){
        showResizeHandles();float x=content.getLeft()+content.getWidth()/2f,y=content.getTop()+69;
        assertTrue(touch(dock,MotionEvent.ACTION_DOWN,x,y));
        assertEquals(View.GONE,dock.getChildAt(4).getVisibility());assertTrue(output.isEmpty());
        assertFalse(dock.touchRegion().contains(content.getLeft()-8,content.getTop()-8));
        touch(dock,MotionEvent.ACTION_MOVE,200,10);touch(dock,MotionEvent.ACTION_MOVE,x,y);touch(dock,MotionEvent.ACTION_UP,x,y);
        assertEquals(1,output.size());assertFalse(output.get(0).isEmpty());
        showResizeHandles();assertEquals(View.VISIBLE,dock.getChildAt(4).getVisibility());
    }
    @Test public void outerHandlesRemainInsideCanvasWhenMovedToScreenEdges(){
        showResizeHandles();View bar=dock.getChildAt(3);
        touch(bar,MotionEvent.ACTION_DOWN,160,14);touch(bar,MotionEvent.ACTION_MOVE,-1000,-1000);touch(bar,MotionEvent.ACTION_UP,-1000,-1000);layout();
        assertTrue(content.getLeft()>=12);assertTrue(content.getTop()>=12);
        touch(bar,MotionEvent.ACTION_DOWN,160,14);touch(bar,MotionEvent.ACTION_MOVE,2000,2000);touch(bar,MotionEvent.ACTION_UP,2000,2000);layout();
        assertTrue(content.getRight()+10<=dock.getWidth());assertTrue(bar.getBottom()+10<=dock.getHeight());
    }
    @Test public void transparentCanvasOnlyAcceptsNewTouchesOnSurface(){
        assertTrue(dock.isFloating());assertEquals(900,dock.getHeight());
        assertEquals(320,content.getWidth());
        assertEquals(Color.TRANSPARENT,((ColorDrawable)dock.getBackground()).getColor());
        assertFalse(dock.touchBounds().contains(10,10));
        assertFalse(touch(dock,MotionEvent.ACTION_DOWN,10,10));
        assertEquals(content.getTop(),dock.touchBounds().top);
        assertEquals(content.getBottom()+28,dock.touchBounds().bottom);
        View bar=dock.getChildAt(3);assertEquals(content.getBottom(),bar.getTop());assertFalse(bar instanceof android.widget.TextView);
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
