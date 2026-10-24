package net.neomenia.glideprototype;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.TextView;
import java.time.Duration;
import net.neomenia.inputcore.SymbolCatalog;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class SymbolPickerViewTest {
    private SharedPreferences prefs;
    private SymbolPickerView picker;
    private String committed;
    private org.robolectric.android.controller.ActivityController<android.app.Activity> controller;
    @Before public void create() throws Exception {
        controller=Robolectric.buildActivity(android.app.Activity.class).setup();
        Context context=controller.get();prefs=InputPreferences.get(context);prefs.edit().clear().commit();
        picker=new SymbolPickerView(context,prefs,new GlideKeyboardView.Listener(){
            public void text(String value,boolean japanese){committed=value;}
            public void action(String value){}
            public void preview(String route,String result){}
        },()->{});
        controller.get().setContentView(picker);controller.visible();
        picker.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY));picker.layout(0,0,720,500);
    }
    @After public void close(){controller.close();}
    private View find(View root,String text){
        if(root instanceof TextView label&&text.contentEquals(label.getText()))return root;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    private GridView grid(View root){
        if(root instanceof GridView value)return value;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){GridView found=grid(group.getChildAt(i));if(found!=null)return found;}
        return null;
    }
    private void click(String text){View view=find(picker,text);assertNotNull(text,view);view.performClick();}
    private int positionForOriginal(String value){
        var adapter=grid(picker).getAdapter();
        for(int i=0;i<adapter.getCount();i++)if(adapter.getItem(i) instanceof SymbolCatalog.Entry entry&&entry.originalValue().equals(value))return i;
        throw new AssertionError(value);
    }
    @Test public void selectedSymbolsAndEmojiCommitTheirPresentationSelectors() throws Exception {
        picker.setTypeface(ImeFonts.load(picker.getContext(),2));click("装飾・音楽");
        int sun=positionForOriginal("☀");grid(picker).performItemClick(null,sun,sun);assertEquals("☀\ufe0e",committed);
        assertTrue(prefs.getString("recentSymbols","").contains("☀\ufe0e"));
        click("絵文字");
        String category=SymbolCatalog.load().stream().filter(c->c.name().startsWith("絵文字：")&&c.entries().stream().anyMatch(e->e.originalValue().equals("⌚"))).findFirst().orElseThrow().name();
        click(category.replace("絵文字：",""));int watch=positionForOriginal("⌚");
        grid(picker).performItemClick(null,watch,watch);assertEquals("⌚\ufe0f",committed);
        assertTrue(prefs.getString("recentSymbols","").contains("⌚\ufe0f"));
        grid(picker).setSelection(watch);SettingsActivityTest.capture(picker,"emoji-presentation-preview",720,500);
    }
    @Test public void oldHistoryIsPreservedUntilReselectedAndKeepsTheCorrectMode() throws Exception {
        String stored="[\"☀\",\"⌚\",\"🇯🇵\"]";prefs.edit().putString("recentSymbols",stored).commit();
        picker=new SymbolPickerView(controller.get(),prefs,new GlideKeyboardView.Listener(){
            public void text(String value,boolean japanese){committed=value;}
            public void action(String value){}
            public void preview(String route,String result){}
        },()->{});controller.get().setContentView(picker);
        assertEquals(stored,prefs.getString("recentSymbols",""));click("履歴");
        GridView grid=grid(picker);assertEquals(1,grid.getCount());assertEquals("☀",((SymbolCatalog.Entry)grid.getAdapter().getItem(0)).value());
        grid.performItemClick(null,0,0);assertEquals("☀\ufe0e",committed);
        click("絵文字");click("履歴");assertEquals(2,grid.getCount());
        assertEquals("⌚",((SymbolCatalog.Entry)grid.getAdapter().getItem(0)).value());assertEquals("🇯🇵",((SymbolCatalog.Entry)grid.getAdapter().getItem(1)).value());
        grid.performItemClick(null,0,0);assertEquals("⌚\ufe0f",committed);
        grid.performItemClick(null,1,1);assertEquals("🇯🇵",committed);
    }
    @Test public void hidesDisabledTabsAndRecoversSelectionOnReopening(){
        click("括弧");assertTrue(find(picker,"括弧").isSelected());
        prefs.edit().putBoolean(SymbolCategoryPreferences.key("括弧"),false).putBoolean(SymbolCategoryPreferences.key("履歴"),false).commit();
        picker.reloadCategories();assertNull(find(picker,"括弧"));assertNull(find(picker,"履歴"));assertTrue(find(picker,"句読点・文書").isSelected());
        assertEquals("、",((SymbolCatalog.Entry)grid(picker).getAdapter().getItem(0)).value());
        prefs.edit().putBoolean(SymbolCategoryPreferences.key("括弧"),true).commit();picker.reloadCategories();click("括弧");
        assertEquals("「",((SymbolCatalog.Entry)grid(picker).getAdapter().getItem(0)).value());
        SettingsActivityTest.capture(picker,"symbol-picker-brackets",720,500);
        picker.setTypeface(ImeFonts.load(picker.getContext(),1));
        SettingsActivityTest.capture(picker,"symbol-picker-brackets-noto",720,500);
        for(int i=0;i<grid(picker).getChildCount();i++){
            View cell=grid(picker).getChildAt(i);if(!(cell instanceof WidthLabelView))continue;
            assertEquals(0,cell.getScrollX());assertEquals(0,cell.getScrollY());
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(cell.getWidth(),cell.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
            try{
                cell.draw(new android.graphics.Canvas(bitmap));int ink=0;
                for(int y=0;y<cell.getHeight()-12;y++)for(int x=0;x<cell.getWidth();x++){
                    int pixel=bitmap.getPixel(x,y);
                    if(android.graphics.Color.red(pixel)>70&&android.graphics.Color.green(pixel)>70&&android.graphics.Color.blue(pixel)>70)ink++;
                }
                assertTrue(((TextView)cell).getText().toString(),ink>0);
            }finally{bitmap.recycle();}
        }
        picker.setTypeface(ImeFonts.load(picker.getContext(),2));click("技術記号");
        SettingsActivityTest.capture(picker,"symbol-picker-bizin-symbols",720,500);
        click("囲み日本語");SettingsActivityTest.capture(picker,"symbol-picker-bizin-japanese",720,500);
    }
    private View findType(View root,Class<?> type){
        if(type.isInstance(root))return root;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findType(group.getChildAt(i),type);if(found!=null)return found;}
        return null;
    }
    private void swipe(float from,float to,float y){
        long time=android.os.SystemClock.uptimeMillis();int[] actions={MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP};
        float[] positions={from,(from+to)/2,to,to};
        for(int i=0;i<actions.length;i++){MotionEvent event=MotionEvent.obtain(time,time+i*16,actions[i],positions[i],y,0);picker.dispatchTouchEvent(event);event.recycle();}
    }
    @Test public void categoryStripScrollsAndReceivesRealTapsWithoutChangingCategoryOnDrag(){
        android.widget.HorizontalScrollView scroll=(android.widget.HorizontalScrollView)findType(picker,android.widget.HorizontalScrollView.class);
        scroll.setSmoothScrollingEnabled(false);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400));scroll.scrollTo(0,0);
        swipe(600,100,24);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        assertTrue(find(picker,"句読点・文書").isSelected());assertTrue(scroll.getScrollX()>0);
        // Robolectric does not render the frames that settle OverScroller's fling.
        ((android.widget.OverScroller)org.robolectric.util.ReflectionHelpers.getField(scroll,"mScroller")).abortAnimation();
        View tab=find(picker,"囲み日本語");scroll.scrollTo(tab.getLeft(),0);
        float x=tab.getLeft()-scroll.getScrollX()+tab.getWidth()/2f;long time=android.os.SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent event=MotionEvent.obtain(time,time+16,action,x,24,0);picker.dispatchTouchEvent(event);event.recycle();}
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        assertTrue("tap="+x+", scroll="+scroll.getScrollX()+", tab="+tab.getLeft()+"/"+tab.getWidth()+", viewport="+scroll.getWidth(),tab.isSelected());
    }
    @Test public void dividerAndPaddingCannotCommitTextAndRareItemsKeepTheirCorrectMapping() throws Exception {
        click("囲み英数字");GridView grid=grid(picker);android.widget.ListAdapter adapter=grid.getAdapter();
        assertEquals("⑳",((SymbolCatalog.Entry)adapter.getItem(19)).value());
        for(int position=20;position<30;position++){
            assertNull(adapter.getItem(position));assertFalse(adapter.isEnabled(position));grid.performItemClick(null,position,position);assertNull(committed);
        }
        assertEquals("㉑",((SymbolCatalog.Entry)adapter.getItem(30)).value());grid.performItemClick(null,30,30);assertEquals("㉑",committed);
        java.util.List<SymbolCatalog.Entry> actual=new java.util.ArrayList<>();for(int i=0;i<adapter.getCount();i++)if(adapter.getItem(i) instanceof SymbolCatalog.Entry entry)actual.add(entry);
        assertEquals(SymbolCatalog.load().stream().filter(c->c.name().equals("囲み英数字")).findFirst().orElseThrow().entries(),actual);
        SettingsActivityTest.capture(picker,"symbol-frequency-divider",720,500);
        click("特殊記号");assertNull(adapter.getItem(0));assertFalse(adapter.isEnabled(0));assertTrue(adapter.isEnabled(6));
        SettingsActivityTest.capture(picker,"symbol-frequency-all-rare",720,500);
    }
    @Test public void allDisabledHasAnEmptyStateAndCanBeEnabledAgain() throws Exception {
        var edit=prefs.edit().putBoolean(SymbolCategoryPreferences.key("履歴"),false);
        for(var category:SymbolCatalog.load())edit.putBoolean(SymbolCategoryPreferences.key(category.name()),false);edit.commit();picker.reloadCategories();
        assertEquals(0,grid(picker).getCount());click("絵文字");assertEquals(0,grid(picker).getCount());
        assertNotNull(find(picker,"表示するカテゴリがありません。設定の「入力」→「総合記号ボード」で有効にしてください。"));
        prefs.edit().putBoolean(SymbolCategoryPreferences.key("絵文字：顔・感情"),true).commit();picker.reloadCategories();assertTrue(grid(picker).getCount()>0);
    }
    @Test public void hiddenHistoryStillRecordsAndPrivateModeDoesNotRecord(){
        prefs.edit().putBoolean(SymbolCategoryPreferences.key("履歴"),false).commit();picker.reloadCategories();
        GridView grid=grid(picker);grid.performItemClick(null,0,0);assertEquals("、",committed);assertTrue(prefs.getString("recentSymbols","").contains("、"));
        picker.setPrivateMode(true);grid.performItemClick(null,1,1);assertFalse(prefs.getString("recentSymbols","").contains("。"));
        picker.setPrivateMode(false);prefs.edit().putBoolean(SymbolCategoryPreferences.key("履歴"),true).commit();picker.reloadCategories();click("履歴");assertEquals(1,grid.getCount());
    }
    @Test public void swipingSkipsDisabledCategoriesAndTabsInterruptAnimation(){
        prefs.edit().putBoolean(SymbolCategoryPreferences.key("括弧"),false).commit();picker.reloadCategories();
        long time=android.os.SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){
            MotionEvent event=MotionEvent.obtain(time,time,action,action==MotionEvent.ACTION_DOWN?500:150,150,0);picker.dispatchTouchEvent(event);event.recycle();
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        assertTrue(find(picker,"数学").isSelected());
        click("矢印");assertTrue(find(picker,"矢印").isSelected());
    }
}
