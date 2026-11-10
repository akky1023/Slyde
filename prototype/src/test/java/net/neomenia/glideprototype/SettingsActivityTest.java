package net.neomenia.glideprototype;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
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
    private static View find(View view,String text){
        if(view instanceof TextView label&&text.contentEquals(label.getText()))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),text);if(result!=null)return result;}
        return null;
    }
    private static void click(View root,String label){View view=find(root,label);assertNotNull(label,view);assertTrue(label,view instanceof MaterialButton);view.performClick();}
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
    @Test public void floatingSizeSettingsDoNotChangeNormalLayoutOrEnableFloating(){verifyIndependentSizeSettings(true);}
    @Test public void normalSizeSettingsDoNotChangeFloatingLayoutOrDisableFloating(){verifyIndependentSizeSettings(false);}
    private void verifyIndependentSizeSettings(boolean floating){
        android.content.Context context=org.robolectric.RuntimeEnvironment.getApplication();
        android.content.SharedPreferences prefs=InputPreferences.get(context);
        prefs.edit().clear().putBoolean("floatingKeyboard",!floating)
            .putFloat("dockPortrait.width",.9f).putFloat("dockPortrait.keyHeight",210)
            .putFloat("floatingPortrait.width",.75f).putFloat("floatingPortrait.keyHeight",170).apply();
        java.util.Map<String,?> before=prefs.getAll();
        android.content.Intent intent=new android.content.Intent(context,LayoutSettingsActivity.class).putExtra(LayoutSettingsActivity.EXTRA_FLOATING,floating);
        try(ActivityController<LayoutSettingsActivity> controller=Robolectric.buildActivity(LayoutSettingsActivity.class,intent).setup().visible()){
            LayoutSettingsActivity activity=controller.get();View root=activity.getWindow().getDecorView();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();layout(root);
            KeyboardDockView dock=(KeyboardDockView)findType(root,KeyboardDockView.class);
            assertEquals(floating,dock.isFloating());assertTrue(dock.isEditing());
            View keyboard=dock.getChildAt(0),overlay=dock.getChildAt(1);
            int width=keyboard.getWidth();
            float x=keyboard.getRight(),y=keyboard.getTop()+keyboard.getHeight()/2f-overlay.getTop();
            drag(overlay,x,y,x-30);layout(root);assertTrue(keyboard.getWidth()<width);
            click(root,"保存");
            String edited=floating?"floatingPortrait.":"dockPortrait.",other=floating?"dockPortrait.":"floatingPortrait.";
            assertTrue(prefs.getFloat(edited+"width",0)<(Float)before.get(edited+"width"));
            assertEquals(before.get(other+"width"),prefs.getAll().get(other+"width"));
            assertEquals(before.get(other+"keyHeight"),prefs.getAll().get(other+"keyHeight"));
            assertEquals(!floating,prefs.getBoolean("floatingKeyboard",floating));
        }
    }
    @Test public void settingsMenuOffersInPlaceAdjustmentAndSeparateSettings(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup().visible()){
            SettingsActivity activity=controller.get();int[] invoked={0,0,0};
            ImeSettingsMenu menu=new ImeSettingsMenu(activity,()->invoked[0]++,()->invoked[1]++,()->invoked[2]++);
            activity.setContentView(menu);capture(menu,"ime-settings-menu",360,280);
            assertEquals(2,menu.getChildCount());assertEquals("戻る",((ViewGroup)menu.getChildAt(0)).getChildAt(0).getContentDescription());
            assertEquals(0,menu.getChildAt(0).getLeft());assertEquals(0,menu.getChildAt(0).getTop());
            find(menu,"キーボードの大きさ/位置変更").performClick();assertEquals(1,invoked[1]);assertEquals(0,invoked[2]);
            find(menu,"その他の設定").performClick();assertEquals(1,invoked[2]);
            ((ViewGroup)menu.getChildAt(0)).getChildAt(0).performClick();assertEquals(1,invoked[0]);
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
