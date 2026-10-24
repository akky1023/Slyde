package net.neomenia.glideprototype;

import android.view.MotionEvent;
import android.widget.FrameLayout;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class SymbolBoardsNavigationTest {
    @Test public void bothTwelveKeyBoardsKeepCustomKeysReturnAndStayBehavior(){
        var context=RuntimeEnvironment.getApplication();List<String> output=new ArrayList<>(),actions=new ArrayList<>();
        GlideKeyboardView keyboard=new GlideKeyboardView(context,new GlideKeyboardView.Listener(){
            public void text(String value,boolean japanese){output.add(value);}
            public void action(String value){actions.add(value);}
            public void preview(String route,String result){}
        });
        FrameLayout parent=new FrameLayout(context);parent.addView(keyboard);keyboard.layout(0,0,1000,400);
        String[] first=SymbolBoards.defaults(0),second=SymbolBoards.defaults(1);first[0]="一号";second[0]="二号";second[1]="";
        keyboard.setSymbolBoard(0,first,true);keyboard.setSymbolBoard(1,second,false);
        tap(keyboard,75,50);assertEquals(List.of("alphabet"),actions);assertTrue(keyboard.isJapanese());
        // S -> K still switches to numbers after removing the sidebar number toggle.
        long time=android.os.SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){
            MotionEvent event=MotionEvent.obtain(time,time,action,500,action==MotionEvent.ACTION_DOWN?150:50,0);
            keyboard.onTouchEvent(event);event.recycle();
        }
        tap(keyboard,267,350);tap(keyboard,267,50); // 記号1, custom first key.
        tap(keyboard,267,50); // Auto-returned to numbers.
        assertEquals(List.of("一号","1"),output);
        tap(keyboard,733,350);tap(keyboard,267,50);tap(keyboard,267,50); // 記号2 stays open.
        assertEquals(List.of("一号","1","二号","二号"),output);
        tap(keyboard,500,50);tap(keyboard,267,50); // Empty key returns to numbers.
        assertEquals("1",output.get(output.size()-1));assertFalse(actions.contains("symbols"));
    }
    private static void tap(GlideKeyboardView view,float x,float y){
        long time=android.os.SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){
            MotionEvent event=MotionEvent.obtain(time,time,action,x,y,0);view.onTouchEvent(event);event.recycle();
        }
    }
}
