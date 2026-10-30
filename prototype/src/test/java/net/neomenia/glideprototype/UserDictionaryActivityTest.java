package net.neomenia.glideprototype;

import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.google.android.material.textfield.TextInputEditText;
import androidx.appcompat.app.AlertDialog;
import java.util.*;
import net.neomenia.inputcore.UserDictionary;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class UserDictionaryActivityTest {
    private static View find(View root,String value){
        if(root instanceof TextView text&&value.contentEquals(text.getText()))return root;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),value);if(found!=null)return found;}
        return null;
    }
    private static List<TextInputEditText> fields(View root){
        List<TextInputEditText> result=new ArrayList<>();if(root instanceof TextInputEditText field)result.add(field);
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.addAll(fields(group.getChildAt(i)));return result;
    }
    private static AlertDialog dialog(){Shadows.shadowOf(Looper.getMainLooper()).idle();return (AlertDialog)ShadowDialog.getLatestDialog();}
    private static void idle(UserDictionaryActivity activity)throws Exception {
        java.lang.reflect.Field field=UserDictionaryActivity.class.getDeclaredField("busy");field.setAccessible(true);
        long deadline=System.nanoTime()+5_000_000_000L;
        do{Shadows.shadowOf(Looper.getMainLooper()).idle();if(!field.getBoolean(activity))return;Thread.sleep(10);}while(System.nanoTime()<deadline);
        fail("辞書処理が完了しませんでした");
    }
    @Test public void settingsOpensDictionaryManagement(){
        try(ActivityController<SettingsActivity> controller=Robolectric.buildActivity(SettingsActivity.class).setup()){
            SettingsActivity activity=controller.get();View root=activity.getWindow().getDecorView();find(root,"変換").performClick();find(root,"カスタム辞書").performClick();
            assertEquals(UserDictionaryActivity.class.getName(),Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        }
    }
    @Test public void editorSelectsAndPersistsBothCandidatePolicies()throws Exception {
        UserDictionaryStore store=UserDictionaryStore.get(RuntimeEnvironment.getApplication());store.save(new UserDictionary(List.of()));
        try(ActivityController<UserDictionaryActivity> controller=Robolectric.buildActivity(UserDictionaryActivity.class).setup()){
            UserDictionaryActivity activity=controller.get();View root=activity.getWindow().getDecorView();idle(activity);
            for(int mode:new int[]{1,2}){
                find(root,"単語を追加").performClick();AlertDialog dialog=dialog();View content=dialog.getWindow().getDecorView();
                var kind=(com.google.android.material.textfield.MaterialAutoCompleteTextView)find(content,"通常");assertNotNull(kind);
                String label=mode==1?"除外（出さない）":"抑制（優先度を下げる）";kind.setText(label,false);kind.getOnItemClickListener().onItemClick(null,kind,mode,mode);
                List<TextInputEditText> fields=fields(content);fields.get(0).setText("はし");fields.get(1).setText(mode==1?"橋":"箸");assertFalse(fields.get(2).isEnabled());dialog.getButton(-1).performClick();idle(activity);
            }
            assertTrue(store.load().isExcluded("はし","橋"));assertTrue(store.load().isDemoted("はし","箸"));assertNotNull(find(root,"橋\nはし · 除外"));assertNotNull(find(root,"箸\nはし · 抑制"));
        }
    }
    @Test public void addEditDeletePersistAndInvalidInputKeepsDialogOpen()throws Exception {
        UserDictionaryStore store=UserDictionaryStore.get(RuntimeEnvironment.getApplication());store.save(new UserDictionary(List.of()));
        try(ActivityController<UserDictionaryActivity> controller=Robolectric.buildActivity(UserDictionaryActivity.class).setup()){
            UserDictionaryActivity activity=controller.get();View root=activity.getWindow().getDecorView();idle(activity);
            assertEquals(RuntimeEnvironment.getApplication().getFilesDir(),activity.getFilesDir());
            assertSame(store,UserDictionaryStore.get(activity));
            find(root,"単語を追加").performClick();AlertDialog dialog=dialog();
            dialog.getButton(-1).performClick();assertTrue(dialog.isShowing());
            List<TextInputEditText> fields=fields(dialog.getWindow().getDecorView());fields.get(0).setText("スライド");fields.get(1).setText("Slyde");fields.get(3).setText("メモ");dialog.getButton(-1).performClick();idle(activity);
            assertEquals(List.of("Slyde"),store.load().exact("すらいど"));assertNotNull(find(root,"Slyde\nすらいど · 名詞"));
            find(root,"Slyde\nすらいど · 名詞").performClick();dialog=dialog();fields=fields(dialog.getWindow().getDecorView());assertEquals("メモ",fields.get(3).getText().toString());fields.get(1).setText("SLYDE");dialog.getButton(-1).performClick();idle(activity);
            assertEquals(List.of("SLYDE"),store.load().exact("すらいど"));
            find(root,"SLYDE\nすらいど · 名詞").performClick();dialog=dialog();dialog.getButton(-3).performClick();
            dialog().getButton(-1).performClick();idle(activity);assertTrue(store.load().entries().isEmpty());
        }
    }
}
