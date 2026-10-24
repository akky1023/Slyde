package net.neomenia.glideprototype;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w360dp-h800dp")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class SettingsActivityTest {
    @Test public void symbolCategorySwitchesPersistAndNavigateBackToInput() throws Exception {
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity activity=controller.get();InputPreferences.get(activity).edit().clear().commit();
            View root=activity.getWindow().getDecorView();click(root,"入力");click(root,"総合記号ボード");
            assertEquals(net.neomenia.inputcore.SymbolCatalog.load().size()+1,count(root,MaterialSwitch.class));
            for(String name:new String[]{"括弧","数学","囲み日本語","囲みハングル","組み文字","特殊記号","履歴を表示","顔・感情"})assertTrue(name,((MaterialSwitch)find(root,name)).isChecked());
            assertNull(find(root,"よく使う記号"));
            ((MaterialSwitch)find(root,"括弧")).setChecked(false);((MaterialSwitch)find(root,"履歴を表示")).setChecked(false);
            assertFalse(SymbolCategoryPreferences.enabled(InputPreferences.get(activity),"括弧"));
            capture(root,"symbol-category-settings");
            activity.onBackPressed();assertNotNull(find(root,"記号入力ボード1"));click(root,"総合記号ボード");
            assertFalse(((MaterialSwitch)find(root,"括弧")).isChecked());assertFalse(((MaterialSwitch)find(root,"履歴を表示")).isChecked());
        }
    }
    private static View find(View view,String text){
        if(view instanceof TextView label&&text.contentEquals(label.getText()))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),text);if(result!=null)return result;}
        return null;
    }
    private static int count(View view,Class<?> type){int result=type.isInstance(view)?1:0;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result+=count(group.getChildAt(i),type);return result;}
    private static void click(View root,String label){View view=find(root,label);assertNotNull(label,view);assertTrue(label,view instanceof MaterialButton);view.performClick();}
    @Test public void launcherHasOnlyFourSettingsGroupsAndNoPracticeScreen(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity activity=controller.get();View root=activity.getWindow().getDecorView();
            for(String name:new String[]{"入力","変換","表示","このアプリについて"})assertTrue(name,find(root,name) instanceof MaterialButton);
            for(String removed:new String[]{"入力を練習する","入力と句読点","変換候補とAI","表示とフォント","大きさ・位置","記号入力ボード"})assertNull(removed,find(root,removed));
            assertEquals(0,count(root,GlideKeyboardView.class));assertEquals(0,count(root,TextInputEditText.class));
            capture(root,"settings-home");
            click(root,"入力");assertNotNull(find(root,"記号入力ボード1"));assertNotNull(find(root,"記号入力ボード2"));assertNull(find(root,"大きさ・位置"));
            click(root,"記号入力ボード1");assertEquals(12,count(root,TextInputEditText.class));click(root,"保存");assertEquals(0,count(root,TextInputEditText.class));
            activity.onBackPressed();click(root,"表示");assertNotNull(find(root,"大きさ・位置"));assertNull(find(root,"記号入力ボード1"));capture(root,"settings-display");
            activity.onBackPressed();click(root,"このアプリについて");assertNotNull(find(root,"ライセンス"));
        }
    }
    @Test public void materialSwitchesSaveToTheExistingPreferenceFile(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity activity=controller.get();View root=activity.getWindow().getDecorView();click(root,"表示");
            MaterialSwitch toggle=(MaterialSwitch)find(root,"AI候補の「AI」を表示");assertNotNull(toggle);toggle.setChecked(false);
            assertFalse(InputPreferences.get(activity).getBoolean("showAiBadges",true));activity.onBackPressed();click(root,"表示");
            assertFalse(((MaterialSwitch)find(root,"AI候補の「AI」を表示")).isChecked());
        }
    }
    private static View findType(View view,Class<?> type){
        if(type.isInstance(view))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findType(group.getChildAt(i),type);if(found!=null)return found;}return null;
    }
    private static void layout(View root){root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,360,800);}
    private static void drag(View overlay,float x,float y,float target){
        long time=android.os.SystemClock.uptimeMillis();
        for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_MOVE,android.view.MotionEvent.ACTION_UP}){
            android.view.MotionEvent event=android.view.MotionEvent.obtain(time,time,action,action==android.view.MotionEvent.ACTION_DOWN?x:target,y,0);overlay.dispatchTouchEvent(event);event.recycle();
        }
    }
    @Test public void sidebarBoundariesDragOnTheActualKeyboardAndSave(){
        try(ActivityController<LayoutSettingsActivity> controller=Robolectric.buildActivity(LayoutSettingsActivity.class).setup().visible()){
            LayoutSettingsActivity activity=controller.get();View root=activity.getWindow().getDecorView();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();layout(root);
            KeyboardDockView dock=(KeyboardDockView)findType(root,KeyboardDockView.class);assertTrue(dock.isEditing());
            View keyboard=dock.getChildAt(0),overlay=dock.getChildAt(1);int width=keyboard.getWidth(),height=keyboard.getHeight(),left=keyboard.getLeft(),top=keyboard.getTop();
            capture(root,"layout-preview");click(root,"サイドバーの幅　左 15%・右 15%");layout(root);
            float y=keyboard.getTop()-dock.toolbarHeight()+keyboard.getHeight()/2f;
            drag(overlay,keyboard.getLeft()+width*.15f,y,keyboard.getLeft()+width*.25f);layout(root);
            assertNotNull(find(root,"枠の調整に戻る　左 25%・右 15%"));
            drag(overlay,keyboard.getLeft()+width*.85f,y,keyboard.getLeft()+width*.92f);layout(root);
            assertNotNull(find(root,"枠の調整に戻る　左 25%・右 8%"));
            assertEquals(width,keyboard.getWidth());assertEquals(height,keyboard.getHeight());assertEquals(left,keyboard.getLeft());assertEquals(top,keyboard.getTop());
            capture(root,"sidebar-drag");click(root,"保存");
            assertEquals(25,InputPreferences.get(activity).getInt("dockPortrait.leftSidePercent",0));assertEquals(8,InputPreferences.get(activity).getInt("dockPortrait.rightSidePercent",0));
        }
    }
    @Test public void settingsMenuOffersInPlaceAdjustmentAndSeparateSettings(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup().visible()){
            SettingsActivity activity=controller.get();View root=activity.getWindow().getDecorView();View anchor=find(root,"表示");int[] invoked={0,0};
            androidx.appcompat.widget.PopupMenu menu=ImeSettingsMenu.show(activity,anchor,()->invoked[0]++,()->invoked[1]++);
            assertEquals("大きさ・位置",menu.getMenu().getItem(0).getTitle());assertEquals("その他の設定",menu.getMenu().getItem(1).getTitle());
            menu.getMenu().performIdentifierAction(1,0);assertEquals(1,invoked[0]);assertEquals(0,invoked[1]);
            menu.getMenu().performIdentifierAction(2,0);assertEquals(1,invoked[1]);menu.dismiss();
        }
    }
    @Test public void themeUsesBlueAndReadableMaterialColorRoles(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            android.content.Context context=controller.get();int surface=SettingsUi.surface(context);
            int text=com.google.android.material.color.MaterialColors.getColor(context,com.google.android.material.R.attr.colorOnSurface,0);
            int primary=com.google.android.material.color.MaterialColors.getColor(context,com.google.android.material.R.attr.colorPrimary,0);
            float[] hsv=new float[3];android.graphics.Color.colorToHSV(primary,hsv);assertTrue(hsv[0]>190&&hsv[0]<240);
            assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(text,surface)>=4.5);
        }
    }
    private static void capture(View view,String name){
        capture(view,name,360,800);
    }
    static void capture(View view,String name,int width,int height){
        String output=System.getProperty("glide.screenshotDir");if(output==null)return;
        // Floating Robolectric windows do not always attach the scroll-indicator helper.
        clearScrollIndicators(view);
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.drawColor(SettingsUi.surface(view.getContext()));view.draw(canvas);
        java.io.File directory=new java.io.File(output);directory.mkdirs();
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(directory,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}catch(java.io.IOException error){throw new AssertionError(error);}finally{bitmap.recycle();}
    }
    private static void clearScrollIndicators(View view){view.setScrollIndicators(0);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)clearScrollIndicators(group.getChildAt(i));}
}
