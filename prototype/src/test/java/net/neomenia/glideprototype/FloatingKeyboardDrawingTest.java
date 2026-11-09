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
