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
    private static AlertDialog dialog(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(350));return (AlertDialog)ShadowDialog.getLatestDialog();}
    private static View menu(UserDictionaryActivity activity)throws Exception {
        var field=UserDictionaryActivity.class.getDeclaredField("actionPopup");field.setAccessible(true);return ((PopupWindow)field.get(activity)).getContentView();
    }
    private static void openDefault(UserDictionaryActivity activity){((View)find(activity.getWindow().getDecorView(),"標準辞書").getParent()).performClick();}
    private static void idle(UserDictionaryActivity activity)throws Exception {
        java.lang.reflect.Field field=UserDictionaryActivity.class.getDeclaredField("busy");field.setAccessible(true);
        long deadline=System.nanoTime()+5_000_000_000L;
        do{Shadows.shadowOf(Looper.getMainLooper()).idle();if(!field.getBoolean(activity))return;Thread.sleep(10);}while(System.nanoTime()<deadline);
        fail("辞書処理が完了しませんでした");
    }
    @Test public void dictionaryUsesFloatingAddButtonAndThreeColumnTable()throws Exception {
        UserDictionaryStore.get(RuntimeEnvironment.getApplication()).save(new UserDictionary(List.of(new UserDictionary.Entry("はし","橋","名詞",""))));
        try(ActivityController<UserDictionaryActivity> controller=Robolectric.buildActivity(UserDictionaryActivity.class).setup()){
            UserDictionaryActivity activity=controller.get();idle(activity);View root=activity.getWindow().getDecorView();
            assertNotNull(find(root,"標準辞書"));assertNull(find(root,"橋"));assertNull(find(root,"単語を追加"));
            assertTrue(find(root,"辞書を追加") instanceof com.google.android.material.floatingactionbutton.FloatingActionButton);openDefault(activity);
            assertTrue(find(root,"単語を追加") instanceof com.google.android.material.floatingactionbutton.FloatingActionButton);
            LinearLayout header=(LinearLayout)find(root,"単語").getParent();assertEquals(3,header.getChildCount());assertEquals("ヨミ",((TextView)header.getChildAt(1)).getText().toString());assertEquals("品詞",((TextView)header.getChildAt(2)).getText().toString());
            LinearLayout row=(LinearLayout)find(root,"橋").getParent();assertEquals(3,row.getChildCount());assertEquals("はし",((TextView)row.getChildAt(1)).getText().toString());
        }
    }
    @Test public void editorSelectsInflectingPartOfSpeechAndPreservesItAcrossPolicies()throws Exception {
        UserDictionaryStore store=UserDictionaryStore.get(RuntimeEnvironment.getApplication());store.save(new UserDictionary(List.of()));
        try(ActivityController<UserDictionaryActivity> controller=Robolectric.buildActivity(UserDictionaryActivity.class).setup()){
            UserDictionaryActivity activity=controller.get();idle(activity);openDefault(activity);find(activity.getWindow().getDecorView(),"単語を追加").performClick();
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
            UserDictionaryActivity activity=controller.get();View root=activity.getWindow().getDecorView();idle(activity);openDefault(activity);
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
            UserDictionaryActivity activity=controller.get();View root=activity.getWindow().getDecorView();idle(activity);openDefault(activity);
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
    @Test public void dictionariesCanBeCreatedRenamedAndReceiveExistingWords()throws Exception {
        var store=UserDictionaryStore.get(RuntimeEnvironment.getApplication());var entry=new UserDictionary.Entry("はし","橋","名詞","メモ");store.save(new UserDictionary(List.of(entry)));
        try(ActivityController<UserDictionaryActivity> controller=Robolectric.buildActivity(UserDictionaryActivity.class).setup()){
            var activity=controller.get();idle(activity);View root=activity.getWindow().getDecorView();find(root,"辞書を追加").performClick();
            assertNotNull(find(menu(activity),"辞書を取り込む"));find(menu(activity),"辞書を追加").performClick();
            AlertDialog dialog=dialog();fields(dialog.getWindow().getDecorView()).get(0).setText("仕事");dialog.getButton(-1).performClick();idle(activity);
            assertEquals(2,store.groups().size());assertNull(find(root,"橋"));
            ((View)find(root,"仕事").getParent()).performLongClick();assertNotNull(find(menu(activity),"書き出し"));assertNotNull(find(menu(activity),"削除"));
            find(menu(activity),"リネーム").performClick();dialog=dialog();fields(dialog.getWindow().getDecorView()).get(0).setText("専門用語");dialog.getButton(-1).performClick();idle(activity);
            String target=store.groups().get(1).id();assertEquals("専門用語",store.group(target).name());
            openDefault(activity);
            ((View)find(root,"橋").getParent()).performClick();dialog=dialog();
            var destination=(com.google.android.material.textfield.MaterialAutoCompleteTextView)find(dialog.getWindow().getDecorView(),"標準辞書");destination.setText("専門用語",false);
            dialog.getButton(-1).performClick();idle(activity);assertEquals(List.of(entry),store.group(target).dictionary().entries());assertTrue(store.groups().get(0).dictionary().entries().isEmpty());
            assertNotNull(find(root,"橋"));assertNotNull(find(root,"専門用語"));
            controller.recreate();activity=controller.get();idle(activity);root=activity.getWindow().getDecorView();assertNotNull(find(root,"橋"));
            assertNotNull(find(root,"専門用語"));activity.getOnBackPressedDispatcher().onBackPressed();assertNull(find(root,"橋"));assertNotNull(find(root,"辞書を追加"));
            ((View)find(root,"専門用語").getParent()).performLongClick();find(menu(activity),"削除").performClick();dialog().getButton(-1).performClick();idle(activity);
            assertEquals(1,store.groups().size());assertTrue(store.load().entries().isEmpty());
        }
    }
    @Test public void importCreatesSeparateDictionaryAndExportTargetsLongPressedDictionary()throws Exception {
        var store=UserDictionaryStore.get(RuntimeEnvironment.getApplication());store.save(new UserDictionary(List.of(new UserDictionary.Entry("はし","橋","名詞",""))));
        try(ActivityController<UserDictionaryActivity> controller=Robolectric.buildActivity(UserDictionaryActivity.class).setup()){
            var activity=controller.get();idle(activity);View root=activity.getWindow().getDecorView();
            find(root,"辞書を追加").performClick();find(menu(activity),"辞書を取り込む").performClick();
            var dialog=dialog();fields(dialog.getWindow().getDecorView()).get(0).setText("取り込み辞書");dialog.getButton(-1).performClick();
            var request=Shadows.shadowOf(activity).getNextStartedActivityForResult();assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT,request.intent.getAction());
            assertEquals(1,store.groups().size());
            android.net.Uri uri=android.net.Uri.parse("content://dictionary/import.txt");
            Shadows.shadowOf(activity.getContentResolver()).registerInputStream(uri,new java.io.ByteArrayInputStream("ほし\t星\t名詞\tメモ\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            Shadows.shadowOf(activity).receiveResult(request.intent,android.app.Activity.RESULT_OK,new android.content.Intent().setData(uri));idle(activity);
            assertEquals(2,store.groups().size());assertEquals(List.of("橋"),store.groups().get(0).dictionary().exact("はし"));
            assertEquals(List.of("星"),store.groups().get(1).dictionary().exact("ほし"));assertNotNull(find(root,"取り込み辞書"));assertNull(find(root,"星"));
            ((View)find(root,"標準辞書").getParent()).performLongClick();find(menu(activity),"書き出し").performClick();
            request=Shadows.shadowOf(activity).getNextStartedActivityForResult();assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT,request.intent.getAction());
            android.net.Uri output=android.net.Uri.parse("content://dictionary/export.txt");var bytes=new java.io.ByteArrayOutputStream();
            Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(output,bytes);
            Shadows.shadowOf(activity).receiveResult(request.intent,android.app.Activity.RESULT_OK,new android.content.Intent().setData(output));idle(activity);
            assertTrue(bytes.toString(java.nio.charset.StandardCharsets.UTF_8).contains("橋"));assertFalse(bytes.toString(java.nio.charset.StandardCharsets.UTF_8).contains("星"));
        }
    }
    @Test @Config(sdk={28,33}) public void systemBackDismissesWordEditorWithoutSavingOrLeavingDictionary()throws Exception {
        var store=UserDictionaryStore.get(RuntimeEnvironment.getApplication());var entry=new UserDictionary.Entry("はし","橋","名詞","");store.save(new UserDictionary(List.of(entry)));
        try(ActivityController<UserDictionaryActivity> controller=Robolectric.buildActivity(UserDictionaryActivity.class).setup()){
            var activity=controller.get();idle(activity);openDefault(activity);View root=activity.getWindow().getDecorView();
            ((View)find(root,"橋").getParent()).performClick();var editor=dialog();fields(editor.getWindow().getDecorView()).get(1).setText("未保存");
            editor.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));editor.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));
            assertFalse(editor.isShowing());assertEquals(List.of(entry),store.load().entries());assertNotNull(find(root,"単語を追加"));
            find(root,"単語を追加").performClick();editor=dialog();activity.getOnBackPressedDispatcher().onBackPressed();
            assertFalse(editor.isShowing());assertNotNull(find(root,"単語を追加"));
            activity.getOnBackPressedDispatcher().onBackPressed();assertNotNull(find(root,"辞書を追加"));
        }
    }
    @Test @Config(sdk={28,33}) public void backBeforeInputMethodClosesEditorWithDestinationFocused()throws Exception {
        var store=UserDictionaryStore.get(RuntimeEnvironment.getApplication());var entry=new UserDictionary.Entry("はし","橋","名詞","");store.save(new UserDictionary(List.of(entry)));
        try(ActivityController<UserDictionaryActivity> controller=Robolectric.buildActivity(UserDictionaryActivity.class).setup()){
            var activity=controller.get();idle(activity);openDefault(activity);View root=activity.getWindow().getDecorView();
            for(boolean dropdownOpen:new boolean[]{false,true}){
                ((View)find(root,"橋").getParent()).performClick();var editor=dialog();
                var destination=(com.google.android.material.textfield.MaterialAutoCompleteTextView)find(editor.getWindow().getDecorView(),"標準辞書");destination.requestFocus();if(dropdownOpen)destination.showDropDown();
                View decor=editor.getWindow().getDecorView();decor.dispatchKeyEventPreIme(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));decor.dispatchKeyEventPreIme(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));
                assertFalse(editor.isShowing());assertFalse(destination.isPopupShowing());assertEquals(List.of(entry),store.load().entries());assertNotNull(find(root,"単語を追加"));
            }
        }
    }
}
