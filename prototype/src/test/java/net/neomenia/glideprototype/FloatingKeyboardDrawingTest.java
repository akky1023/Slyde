package net.neomenia.glideprototype;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FloatingKeyboardDrawingTest {
    private int labelHeight(int width,int height,boolean floating){
        GlideKeyboardView keyboard=new GlideKeyboardView(RuntimeEnvironment.getApplication(),new GlideKeyboardView.Listener(){
            public void text(String value,boolean japanese){}public void action(String action){}public void preview(String route,String value){}
        });
        keyboard.setScaleTextToFit(floating);
        keyboard.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));keyboard.layout(0,0,width,height);
        Bitmap buffer=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        try{
            keyboard.draw(new Canvas(buffer));int first=height,last=-1;
            // The first main key is the only white label inside this cell; hints use another color.
            for(int y=0;y<height/4;y++)for(int x=(int)(width*.15f)+2;x<(int)(width*(.15f+.7f/3))-2;x++){
                if(buffer.getPixel(x,y)==Color.WHITE){first=Math.min(first,y);last=Math.max(last,y);}
            }
            assertTrue("The main key label must be visible",last>=first);return last-first+1;
        }finally{buffer.recycle();}
    }
    @Test public void floatingLabelsShrinkForSmallKeysAndNeverGrowPastNormalSize(){
        int normal=labelHeight(400,232,false);
        int smallNormal=labelHeight(240,128,false);
        assertTrue(Math.abs(normal-smallNormal)<=1); // Rasterization rounds at different baselines.
        assertTrue(labelHeight(240,128,true)<smallNormal*.85f);
        assertTrue(labelHeight(240,232,true)<normal);
        assertEquals(labelHeight(800,464,false),labelHeight(800,464,true));
    }
    @Test public void bottomBarAndCornerControlsRenderWithActualKeys() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        var prefs=context.getSharedPreferences("floating-controls",0);prefs.edit().clear().putBoolean("floatingKeyboard",true).apply();
        android.widget.LinearLayout content=new android.widget.LinearLayout(context);content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.addView(new CandidateBarView(context,value->{},value->{}),new android.widget.LinearLayout.LayoutParams(-1,40));
        GlideKeyboardView keys=new GlideKeyboardView(context,new GlideKeyboardView.Listener(){public void text(String value,boolean japanese){}public void action(String action){}public void preview(String route,String value){}});
        keys.setScaleTextToFit(true);content.addView(keys,new android.widget.LinearLayout.LayoutParams(-1,232));
        KeyboardDockView dock=new KeyboardDockView(context,content,prefs,40,height->keys.getLayoutParams().height=height-40);
        new FrameLayout(context).addView(dock);layout(dock);
        View bar=dock.getChildAt(3);assertEquals(content.getBottom(),bar.getTop());
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){
            MotionEvent event=MotionEvent.obtain(0,10,action,160,14,0);bar.dispatchTouchEvent(event);event.recycle();
        }
        assertEquals(View.VISIBLE,dock.getChildAt(4).getVisibility());
        for(int step=0;step<2;step++){
            if(step==1){
                float x=content.getRight()+8,y=bar.getBottom()+8;
                for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){
                    MotionEvent event=MotionEvent.obtain(0,10,action,x-(action==MotionEvent.ACTION_DOWN?0:60),y-(action==MotionEvent.ACTION_DOWN?0:60),0);dock.dispatchTouchEvent(event);event.recycle();
                }
                layout(dock);
            }
            Bitmap buffer=Bitmap.createBitmap(400,900,Bitmap.Config.ARGB_8888);
            try{
                dock.draw(new Canvas(buffer));
                assertEquals(Color.rgb(104,181,255),buffer.getPixel(content.getLeft()-8,content.getTop()-4));
                assertEquals(Color.TRANSPARENT,buffer.getPixel(content.getLeft()-4,content.getTop()-4));
                java.io.File directory=new java.io.File(System.getProperty("glide.screenshotDir"));directory.mkdirs();
                try(var stream=new java.io.FileOutputStream(new java.io.File(directory,"floating-resize-"+step+".png"))){buffer.compress(Bitmap.CompressFormat.PNG,100,stream);}
            }finally{buffer.recycle();}
        }
        assertEquals(260,content.getWidth());assertEquals(172,keys.getHeight());
    }
    private void layout(KeyboardDockView dock){
        dock.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.AT_MOST));
        dock.layout(0,0,dock.getMeasuredWidth(),dock.getMeasuredHeight());
    }
    @Test public void movingSurfaceErasesOldPixelsOnReusedBuffer(){
        Context context=RuntimeEnvironment.getApplication();
        var prefs=context.getSharedPreferences("floating-drawing",0);
        prefs.edit().clear().putBoolean("floatingKeyboard",true).apply();
        View surface=new View(context);surface.setBackgroundColor(Color.BLUE);
        KeyboardDockView dock=new KeyboardDockView(context,surface,prefs,40,height->{});
        new FrameLayout(context).addView(dock);layout(dock);
        Bitmap buffer=Bitmap.createBitmap(400,900,Bitmap.Config.ARGB_8888);
        try{
            Canvas canvas=new Canvas(buffer);dock.draw(canvas);
            int oldX=surface.getLeft()+10,oldY=surface.getBottom()-10;
            assertEquals(Color.BLUE,buffer.getPixel(oldX,oldY));
            View handle=dock.getChildAt(3);
            for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){
                MotionEvent event=MotionEvent.obtain(0,10,action,160,action==MotionEvent.ACTION_DOWN?14:-86,0);
                handle.dispatchTouchEvent(event);event.recycle();
            }
            layout(dock);dock.draw(canvas);
            assertEquals("The old keyboard position must be transparent",Color.TRANSPARENT,buffer.getPixel(oldX,oldY));
            assertEquals(Color.BLUE,buffer.getPixel(surface.getLeft()+10,surface.getTop()+10));
            assertEquals(Color.TRANSPARENT,buffer.getPixel(10,10));
        }finally{buffer.recycle();}
    }
}
