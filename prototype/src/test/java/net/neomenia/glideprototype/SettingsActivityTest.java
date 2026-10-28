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
            click(root,"入力");assertNotNull(find(root,"記号入力ボード1"));assertNotNull(find(root,"記号入力ボード2"));assertNull(find(root,"総合記号ボード"));assertEquals(0,count(root,TextInputEditText.class));
            assertNull(find(root,"記号・絵文字"));capture(root,"settings-conversion-only-input");
            click(root,"記号入力ボード1");assertEquals(12,count(root,TextInputEditText.class));click(root,"保存");
            activity.onBackPressed();click(root,"表示");assertNotNull(find(root,"大きさ・位置"));capture(root,"settings-display");
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
    @Test public void cancelAlphabetOnInputDefaultsOffAndPersists(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity activity=controller.get();InputPreferences.get(activity).edit().clear().apply();
            View root=activity.getWindow().getDecorView();click(root,"変換");
            assertNotNull(find(root,"無変換"));assertNull(find(root,"英字に戻すボタン"));
            MaterialSwitch enabled=(MaterialSwitch)find(root,"無変換を有効にする");
            assertTrue(enabled.isChecked());
            MaterialSwitch toggle=(MaterialSwitch)find(root,"入力再開時に無変換を取り消す");
            assertNotNull(toggle);assertFalse(toggle.isChecked());toggle.setChecked(true);
            assertTrue(InputPreferences.get(activity).getBoolean("cancelAlphabetOnInput",false));
            activity.onBackPressed();click(root,"変換");
            toggle=(MaterialSwitch)find(root,"入力再開時に無変換を取り消す");assertTrue(toggle.isChecked());
            enabled=(MaterialSwitch)find(root,"無変換を有効にする");enabled.setChecked(false);
            assertFalse(toggle.isEnabled());assertTrue(toggle.isChecked());
            assertFalse(InputPreferences.get(activity).getBoolean("alphabetButtonEnabled",true));
            activity.onBackPressed();click(root,"変換");
            toggle=(MaterialSwitch)find(root,"入力再開時に無変換を取り消す");assertFalse(toggle.isEnabled());
            enabled=(MaterialSwitch)find(root,"無変換を有効にする");assertFalse(enabled.isChecked());
            enabled.setChecked(true);assertTrue(toggle.isEnabled());assertTrue(toggle.isChecked());
        }
    }
    @Test public void inputDefaultsAndSavedVowelChoicesAgree(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity activity=controller.get();View root=activity.getWindow().getDecorView();
            InputPreferences.get(activity).edit().clear().apply();click(root,"入力");
            for(String name:new String[]{"XEUをグライド入力","XIUをグライド入力","丸括弧を半角にする"})assertFalse(((MaterialSwitch)find(root,name)).isChecked());
            ((MaterialSwitch)find(root,"XEUをグライド入力")).setChecked(true);
            activity.onBackPressed();click(root,"入力");
            assertTrue(((MaterialSwitch)find(root,"XEUをグライド入力")).isChecked());
            assertFalse(((MaterialSwitch)find(root,"XIUをグライド入力")).isChecked());
        }
    }
    @Test public void inputModeSettingsSaveAndResetDependsOnMemory(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity activity=controller.get();InputPreferences.get(activity).edit().clear().apply();
            View root=activity.getWindow().getDecorView();click(root,"入力");
            MaterialSwitch remember=(MaterialSwitch)find(root,"日本語・アルファベットの入力モードを記憶する");
            MaterialSwitch reset=(MaterialSwitch)find(root,"3分経過で日本語入力に戻す");
            assertFalse(remember.isChecked());assertFalse(reset.isChecked());assertFalse(reset.isEnabled());
            remember.setChecked(true);assertTrue(reset.isEnabled());reset.setChecked(true);
            activity.onBackPressed();click(root,"入力");
            remember=(MaterialSwitch)find(root,"日本語・アルファベットの入力モードを記憶する");
            reset=(MaterialSwitch)find(root,"3分経過で日本語入力に戻す");
            assertTrue(remember.isChecked());assertTrue(reset.isChecked());assertTrue(reset.isEnabled());
            InputPreferences.get(activity).edit().putBoolean("savedJapaneseInputMode",false).apply();
            remember.setChecked(false);assertFalse(reset.isEnabled());
            assertFalse(InputPreferences.get(activity).contains("savedJapaneseInputMode"));
            capture(root,"settings-input-mode");
        }
    }
    @Test public void punctuationDropdownSavesAllFourPairsAndRestoresLegacyChoices(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity activity=controller.get();View root=activity.getWindow().getDecorView();
            android.content.SharedPreferences prefs=InputPreferences.get(activity);
            String[] styles={"、。","，．","，。","、．"};
            for(int index=0;index<styles.length;index++){
                prefs.edit().putBoolean("commaStyle",index==1||index==2).putBoolean("periodStyle",index==1||index==3).apply();
                click(root,"入力");
                android.widget.AutoCompleteTextView dropdown=(android.widget.AutoCompleteTextView)find(root,styles[index]);assertNotNull(dropdown);
                assertEquals(4,dropdown.getAdapter().getCount());
                for(int choice=0;choice<styles.length;choice++){
                    assertEquals(styles[choice],dropdown.getAdapter().getItem(choice));
                    dropdown.getOnItemClickListener().onItemClick(null,null,choice,choice);
                    net.neomenia.inputcore.JapaneseTypography typography=new net.neomenia.inputcore.JapaneseTypography(prefs.getBoolean("commaStyle",false),prefs.getBoolean("periodStyle",false),false,false);
                    assertEquals(styles[choice],typography.format("、。"));
                }
                activity.onBackPressed();
            }
        }
    }
    @Test public void unifiedParenthesesInheritEitherLegacySideAndCanBeTurnedOff(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity activity=controller.get();View root=activity.getWindow().getDecorView();
            android.content.SharedPreferences prefs=InputPreferences.get(activity);
            for(String legacy:new String[]{"openParenStyle","closeParenStyle"}){
                prefs.edit().clear().putBoolean(legacy,true).apply();click(root,"入力");
                MaterialSwitch toggle=(MaterialSwitch)find(root,"丸括弧を半角にする");assertTrue(toggle.isChecked());
                toggle.setChecked(false);assertFalse(InputPreferences.halfWidthParentheses(prefs));
                activity.onBackPressed();click(root,"入力");
                assertFalse(((MaterialSwitch)find(root,"丸括弧を半角にする")).isChecked());activity.onBackPressed();
            }
        }
    }
    @Test public void displayRemovesExtraLabelsAndAuthorLinksOpenTheirDestinations(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity activity=controller.get();View root=activity.getWindow().getDecorView();click(root,"表示");
            assertNull(find(root,"日本語の変換候補"));assertNull(find(root,"注記"));
            assertNotNull(find(root,"紛らわしい文字の注記を表示（全・半・漢・カナなど）"));
            activity.onBackPressed();click(root,"このアプリについて");
            ViewGroup about=(ViewGroup)find(root,"リポジトリ").getParent();int count=about.getChildCount();
            assertEquals(find(root,"リポジトリ"),about.getChildAt(count-3));assertEquals(find(root,"ライセンス"),about.getChildAt(count-2));assertEquals(find(root,"制作者情報"),about.getChildAt(count-1));
            assertNull(find(root,"akky_1023"));click(root,"リポジトリ");
            assertEquals("https://github.com/akky1023/Slyde",org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity().getDataString());
            capture(root,"settings-about");
            click(root,"制作者情報");android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing());
            TextView name=(TextView)find(dialog.getWindow().getDecorView(),"akky_1023");assertNotNull(name);assertFalse(name instanceof Button);
            ViewGroup content=(ViewGroup)name.getParent();assertEquals(3,content.getChildCount());
            assertTrue(content.getChildAt(0) instanceof android.widget.ImageView);assertNotNull(((android.widget.ImageView)content.getChildAt(0)).getDrawable());
            android.widget.ImageView avatar=(android.widget.ImageView)content.getChildAt(0);
            assertEquals(android.widget.ImageView.ScaleType.CENTER_CROP,avatar.getScaleType());
            assertTrue(((androidx.core.graphics.drawable.RoundedBitmapDrawable)avatar.getDrawable()).isCircular());
            android.text.Spanned linkedName=(android.text.Spanned)name.getText();linkedName.getSpans(0,linkedName.length(),android.text.style.URLSpan.class)[0].onClick(name);
            assertEquals("https://neomenia.net",org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity().getDataString());
            View github=content.getChildAt(2);assertTrue(github instanceof android.widget.ImageButton);assertEquals("GitHub",github.getContentDescription());github.performClick();
            assertEquals("https://github.com/akky1023",org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity().getDataString());
            capture(content,"settings-author",280,280);
            for(int i=0;i<content.getChildCount();i++){View child=content.getChildAt(i);assertEquals(content.getWidth()/2,(child.getLeft()+child.getRight())/2);}
        }
    }
    @Test public void licenseGroupsOpenTheirOwnNoticesAndReturnToTheList(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity activity=controller.get();View root=activity.getWindow().getDecorView();click(root,"このアプリについて");
            String[] groups={"Slyde本体","Mozc","Sumire","zenz"};
            String[] notices={"Copyright (c) 2026 akky_1023","Google","Kazuma Naka","The ggml authors"};
            click(root,"ライセンス");
            for(int index=0;index<groups.length;index++){
                androidx.appcompat.app.AlertDialog list=(androidx.appcompat.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
                assertEquals(groups.length,list.getListView().getAdapter().getCount());
                for(int i=0;i<groups.length;i++)assertEquals(groups[i],list.getListView().getAdapter().getItem(i));
                list.getListView().performItemClick(null,index,index);
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                androidx.appcompat.app.AlertDialog details=(androidx.appcompat.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
                assertNotNull(find(details.getWindow().getDecorView(),groups[index]));
                android.widget.ScrollView scroll=(android.widget.ScrollView)findType(details.getWindow().getDecorView(),android.widget.ScrollView.class);
                String text=((TextView)scroll.getChildAt(0)).getText().toString();assertTrue(text,text.contains(notices[index]));
                if(index==0){assertTrue(text.contains("GitHub Octicons"));assertTrue(text.contains("SIL Open Font License"));}
                if(index==2){assertTrue(text.contains("IPADIC"));assertTrue(text.contains("Data — License"));}
                if(index==3){assertTrue(text.contains("Apache License"));assertTrue(text.contains("JNI bridge"));}
                details.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).performClick();
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            }
        }
    }
    @Test public void conversionWaitSettingIsOptionalAndSaved(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity activity=controller.get();InputPreferences.get(activity).edit().remove("waitForZenz").apply();
            View root=activity.getWindow().getDecorView();click(root,"変換");
            MaterialSwitch toggle=(MaterialSwitch)find(root,"AI変換を待って候補を表示する");
            assertNotNull(toggle);assertFalse(toggle.isChecked());toggle.setChecked(true);
            assertTrue(InputPreferences.get(activity).getBoolean("waitForZenz",false));
            SettingsActivityTest.capture(root,"settings-conversion-wait");
            activity.onBackPressed();click(root,"変換");
            assertTrue(((MaterialSwitch)find(root,"AI変換を待って候補を表示する")).isChecked());
        }
    }
    @Test public void variableWaitModeIsSavedAndFixedWaitRemainsAvailable(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity activity=controller.get();
            InputPreferences.get(activity).edit().putBoolean("waitForZenz",false).putBoolean("variableZenzWait",false).apply();
            View root=activity.getWindow().getDecorView();click(root,"変換");
            android.widget.RadioButton fixed=(android.widget.RadioButton)find(root,"固定：20ms");
            android.widget.RadioButton variable=(android.widget.RadioButton)find(root,"可変：20＋文字数×2 ms");
            assertTrue(fixed.isChecked());assertFalse(variable.isEnabled());
            ((MaterialSwitch)find(root,"AI変換を待って候補を表示する")).setChecked(true);
            assertTrue(variable.isEnabled());variable.performClick();
            assertTrue(InputPreferences.get(activity).getBoolean("variableZenzWait",false));assertFalse(fixed.isChecked());
            activity.onBackPressed();click(root,"変換");
            assertTrue(((android.widget.RadioButton)find(root,"可変：20＋文字数×2 ms")).isChecked());
            capture(root,"settings-variable-wait");
            find(root,"固定：20ms").performClick();assertFalse(InputPreferences.get(activity).getBoolean("variableZenzWait",true));
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
            SettingsActivity activity=controller.get();int[] invoked={0,0,0};
            ImeSettingsMenu menu=new ImeSettingsMenu(activity,()->invoked[0]++,()->invoked[1]++,()->invoked[2]++);
            activity.setContentView(menu);capture(menu,"ime-settings-menu",360,280);
            assertEquals(2,menu.getChildCount());assertEquals("戻る",menu.getChildAt(0).getContentDescription());
            assertEquals(0,menu.getChildAt(0).getLeft());assertEquals(0,menu.getChildAt(0).getTop());
            find(menu,"キーボードの大きさ/位置変更").performClick();assertEquals(1,invoked[1]);assertEquals(0,invoked[2]);
            find(menu,"その他の設定").performClick();assertEquals(1,invoked[2]);
            menu.getChildAt(0).performClick();assertEquals(1,invoked[0]);
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
