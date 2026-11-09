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
        if(value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof TextView text&&value.contentEquals(text.getText()))return root;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),value);if(found!=null)return found;}
        return null;
    }
    private static List<EditText> fields(View root){
        List<EditText> result=new ArrayList<>();if(root instanceof EditText field&&field.getInputType()!=android.text.InputType.TYPE_NULL)result.add(field);
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
    @Test public void dictionaryUsesFloatingAddButtonAndThreeColumnTable()throws Exception {
        UserDictionaryStore.get(RuntimeEnvironment.getApplication()).save(new UserDictionary(List.of(new UserDictionary.Entry("はし","橋","名詞",""))));
        try(ActivityController<UserDictionaryActivity> controller=Robolectric.buildActivity(UserDictionaryActivity.class).setup()){
            UserDictionaryActivity activity=controller.get();idle(activity);View root=activity.getWindow().getDecorView();
            assertTrue(find(root,"単語を追加") instanceof com.google.android.material.floatingactionbutton.FloatingActionButton);
            LinearLayout actions=(LinearLayout)find(root,"辞書を取り込む").getParent();assertSame(actions,find(root,"辞書を書き出す").getParent());assertEquals(LinearLayout.HORIZONTAL,actions.getOrientation());
            LinearLayout header=(LinearLayout)find(root,"単語").getParent();assertEquals(3,header.getChildCount());assertEquals("ヨミ",((TextView)header.getChildAt(1)).getText().toString());assertEquals("品詞",((TextView)header.getChildAt(2)).getText().toString());
            LinearLayout row=(LinearLayout)find(root,"橋").getParent();assertEquals(3,row.getChildCount());assertEquals("はし",((TextView)row.getChildAt(1)).getText().toString());
        }
    }
    @Test public void editorSelectsInflectingPartOfSpeechAndPreservesItAcrossPolicies()throws Exception {
        UserDictionaryStore store=UserDictionaryStore.get(RuntimeEnvironment.getApplication());store.save(new UserDictionary(List.of()));
        try(ActivityController<UserDictionaryActivity> controller=Robolectric.buildActivity(UserDictionaryActivity.class).setup()){
            UserDictionaryActivity activity=controller.get();idle(activity);find(activity.getWindow().getDecorView(),"単語を追加").performClick();
            AlertDialog dialog=dialog();View content=dialog.getWindow().getDecorView();List<EditText> fields=fields(content);
            var pos=(com.google.android.material.textfield.MaterialAutoCompleteTextView)find(content,"名詞");
            var inflection=(com.google.android.material.textfield.MaterialAutoCompleteTextView)find(content,"ワ五");
            assertEquals(View.GONE,((View)inflection.getParent().getParent()).getVisibility());
            List<String> options=new ArrayList<>();for(int i=0;i<pos.getAdapter().getCount();i++)options.add(pos.getAdapter().getItem(i).toString());assertTrue(options.contains("動詞"));assertTrue(options.contains("形容詞"));
            fields.get(0).setText("ぐぐる");fields.get(1).setText("ググる");pos.setText("動詞",false);pos.getOnItemClickListener().onItemClick(null,pos,options.indexOf("動詞"),0);
            assertEquals(View.VISIBLE,((View)inflection.getParent().getParent()).getVisibility());
            inflection.setText("ラ五",false);
            var kind=(com.google.android.material.textfield.MaterialAutoCompleteTextView)find(content,"通常");
            kind.setText("除外（出さない）",false);kind.getOnItemClickListener().onItemClick(null,kind,1,1);
            assertFalse(pos.isEnabled());assertEquals(View.GONE,((View)inflection.getParent().getParent()).getVisibility());
            kind.setText("通常",false);kind.getOnItemClickListener().onItemClick(null,kind,0,0);assertEquals("動詞",pos.getText().toString());assertEquals("ラ五",inflection.getText().toString());
            assertEquals(View.VISIBLE,((View)inflection.getParent().getParent()).getVisibility());
            dialog.getButton(-1).performClick();idle(activity);assertEquals(List.of("ググった"),store.load().exact("ぐぐった"));assertEquals("動詞ラ行五段",store.load().entries().get(0).partOfSpeech());
            View root=activity.getWindow().getDecorView();assertNotNull(find(root,"動詞ラ五"));
            ((View)find(root,"ググる").getParent()).performClick();
            View reopened=dialog().getWindow().getDecorView();assertNotNull(find(reopened,"動詞"));assertNotNull(find(reopened,"ラ五"));
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
                List<EditText> fields=fields(content);fields.get(0).setText("はし");fields.get(1).setText(mode==1?"橋":"箸");assertFalse(find(content,"名詞").isEnabled());dialog.getButton(-1).performClick();idle(activity);
            }
            assertTrue(store.load().isExcluded("はし","橋"));assertTrue(store.load().isDemoted("はし","箸"));assertNotNull(find(root,"橋"));assertNotNull(find(root,"箸"));
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
            List<EditText> fields=fields(dialog.getWindow().getDecorView());fields.get(0).setText("スライド");fields.get(1).setText("Slyde");fields.get(2).setText("メモ");dialog.getButton(-1).performClick();idle(activity);
            assertEquals(List.of("Slyde"),store.load().exact("すらいど"));assertNotNull(find(root,"Slyde"));
            ((View)find(root,"Slyde").getParent()).performClick();dialog=dialog();fields=fields(dialog.getWindow().getDecorView());assertEquals("メモ",fields.get(2).getText().toString());fields.get(1).setText("SLYDE");dialog.getButton(-1).performClick();idle(activity);
            assertEquals(List.of("SLYDE"),store.load().exact("すらいど"));
            ((View)find(root,"SLYDE").getParent()).performClick();dialog=dialog();dialog.getButton(-3).performClick();
            dialog().getButton(-1).performClick();idle(activity);assertTrue(store.load().entries().isEmpty());
        }
    }
}
