package net.neomenia.glideprototype;

import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import net.neomenia.inputcore.UserDictionary;

/** User dictionary management never initializes the conversion engines. */
public final class UserDictionaryActivity extends AppCompatActivity {
    private LinearLayout body;
    private UserDictionary dictionary=new UserDictionary(List.of());
    // Serialize document operations across activity recreation, including pending imports.
    private static final ExecutorService worker=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task,"user-dictionary");thread.setDaemon(true);return thread;});
    private boolean busy=true;
    private String query="";
    private TextView count;
    private final ActivityResultLauncher<String[]> importer=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{
        if(uri==null)return;
        run(()->{
            UserDictionary imported;
            try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("ファイルを開けません");imported=UserDictionaryStore.readImport(in);}
            UserDictionaryStore store=UserDictionaryStore.get(this);UserDictionary merged=store.load().merge(imported);store.save(merged);return merged;
        },"辞書を取り込みました");
    });
    private final ActivityResultLauncher<String> exporter=registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"),uri->{
        if(uri==null)return;
        run(()->{
            UserDictionary saved=UserDictionaryStore.get(this).load();
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                if(out==null)throw new IOException("ファイルを開けません");
                try(Writer writer=new OutputStreamWriter(out,StandardCharsets.UTF_8)){saved.write(writer);}
            }return saved;
        },"辞書を書き出しました");
    });
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(SettingsUi.surface(this));setContentView(root);
        if(android.os.Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.ime());root.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});}
        MaterialToolbar toolbar=new MaterialToolbar(this);toolbar.setTitle("カスタム辞書");toolbar.setNavigationIcon(R.drawable.ic_back);toolbar.setNavigationIconTint(com.google.android.material.color.MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurface,0));toolbar.setNavigationOnClickListener(view->finish());root.addView(toolbar);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);root.addView(actions);
        actions.setPadding(SettingsUi.dp(this,12),0,SettingsUi.dp(this,12),0);
        var importButton=SettingsUi.button(this,"辞書を取り込む",()->{if(!busy)importer.launch(new String[]{"*/*"});});
        var exportButton=SettingsUi.button(this,"辞書を書き出す",()->{if(!busy)exporter.launch("slyde-dictionary.txt");});
        LinearLayout.LayoutParams importParams=new LinearLayout.LayoutParams(0,-2,1);importParams.setMarginEnd(SettingsUi.dp(this,8));
        actions.addView(importButton,importParams);actions.addView(exportButton,new LinearLayout.LayoutParams(0,-2,1));
        TextInputEditText search=new TextInputEditText(this);search.setHint("よみ・単語を検索");search.setSingleLine(true);root.addView(search);
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();render();}public void afterTextChanged(android.text.Editable s){}});
        count=SettingsUi.text(this,"読み込み中…",14);root.addView(count);
        FrameLayout content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout table=new LinearLayout(this);table.setOrientation(LinearLayout.VERTICAL);content.addView(table,new FrameLayout.LayoutParams(-1,-1));
        table.addView(tableRow(new String[]{"単語","ヨミ","品詞"},true));
        ScrollView scroll=new ScrollView(this);table.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,0,0,SettingsUi.dp(this,96));scroll.addView(body);
        FloatingActionButton add=new FloatingActionButton(this);add.setImageResource(R.drawable.ic_add);add.setContentDescription("単語を追加");add.setOnClickListener(view->{if(!busy)edit(null);});
        FrameLayout.LayoutParams addParams=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);addParams.setMargins(SettingsUi.dp(this,16),SettingsUi.dp(this,16),SettingsUi.dp(this,16),SettingsUi.dp(this,16));content.addView(add,addParams);
        busy=false;run(()->UserDictionaryStore.get(this).load(),null);
    }
    private void render(){
        if(body==null)return;body.removeAllViews();if(count==null)return;
        int shown=0,total=0;String normalized=UserDictionary.normalize(query);
        for(UserDictionary.Entry entry:dictionary.entries()){
            if(!entry.reading().contains(normalized)&&!entry.word().contains(query))continue;total++;
            if(shown++<100){
                LinearLayout row=tableRow(new String[]{entry.word().isEmpty()?"すべての単語":entry.word(),entry.reading().isEmpty()?"すべてのよみ":entry.reading(),displayPos(entry.partOfSpeech())},false);
                row.setOnClickListener(view->{if(!busy)edit(entry);});body.addView(row);
            }
        }
        count.setText(busy?"処理中…":dictionary.entries().size()+"件"+(total>100?"（検索結果の先頭100件を表示）":""));
    }
    private static String displayPos(String value){
        return value.equals("抑制単語")?"除外":value.replaceAll("^動詞(.)行五段$","動詞$1五");
    }
    private LinearLayout tableRow(String[] values,boolean header){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(SettingsUi.dp(this,header?48:56));
        if(header)row.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(this,com.google.android.material.R.attr.colorSurfaceVariant,0));
        for(String value:values){
            TextView cell=SettingsUi.text(this,value,14);cell.setGravity(Gravity.CENTER);int padding=SettingsUi.dp(this,8);cell.setPadding(padding,padding,padding,padding);
            if(header)cell.setTypeface(cell.getTypeface(),android.graphics.Typeface.BOLD);
            row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
        }
        if(!header){android.util.TypedValue background=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,background,true);row.setBackgroundResource(background.resourceId);row.setFocusable(true);}
        return row;
    }
    private com.google.android.material.textfield.MaterialAutoCompleteTextView selector(LinearLayout form,String label,List<String> values,String selected){
        TextInputLayout box=new TextInputLayout(this,null,com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);box.setHint(label);
        var input=new com.google.android.material.textfield.MaterialAutoCompleteTextView(box.getContext());input.setInputType(android.text.InputType.TYPE_NULL);input.setSingleLine(true);input.setSimpleItems(values.toArray(new String[0]));input.setText(selected,false);box.addView(input);form.addView(box);return input;
    }
    private TextInputEditText field(LinearLayout form,String name,String value){
        TextInputLayout box=new TextInputLayout(this);box.setHint(name);TextInputEditText text=new TextInputEditText(box.getContext());text.setSingleLine(true);text.setText(value);box.addView(text);form.addView(box);return text;
    }
    private void edit(UserDictionary.Entry original){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);int padding=SettingsUi.dp(this,20);form.setPadding(padding,0,padding,0);
        TextInputEditText reading=field(form,"よみ",original==null?"":original.reading()),word=field(form,"単語",original==null?"":original.word());
        String originalPos=original==null?"名詞":original.partOfSpeech();
        int initial=originalPos.equals("抑制単語")?1:originalPos.equals("抑制")?2:0;
        String ordinaryPos=initial==0?originalPos:"名詞";
        List<String> verbs=new ArrayList<>(),categories=new ArrayList<>();
        for(String value:UserDictionary.partsOfSpeech()){
            if(value.startsWith("動詞")){verbs.add(value);if(!categories.contains("動詞"))categories.add("動詞");}
            else categories.add(value);
        }
        categories.remove("名詞サ変");categories.add(categories.indexOf("名詞")+1,"名詞サ変");
        // Preserve unfamiliar part-of-speech labels from imported dictionaries when editing.
        if(!ordinaryPos.startsWith("動詞")&&!categories.contains(ordinaryPos))categories.add(ordinaryPos);
        if(ordinaryPos.startsWith("動詞")&&!verbs.contains(ordinaryPos))verbs.add(ordinaryPos);
        var pos=selector(form,"品詞",categories,ordinaryPos.startsWith("動詞")?"動詞":ordinaryPos);
        TextInputLayout posBox=(TextInputLayout)form.getChildAt(form.getChildCount()-1);
        List<String> conjugations=new ArrayList<>();for(String value:verbs)conjugations.add(displayPos(value).substring(2));
        var inflection=selector(form,"活用",conjugations,ordinaryPos.startsWith("動詞")?displayPos(ordinaryPos).substring(2):conjugations.get(0));
        TextInputLayout inflectionBox=(TextInputLayout)form.getChildAt(form.getChildCount()-1);
        Runnable updateInflection=()->inflectionBox.setVisibility(pos.isEnabled()&&pos.getText().toString().equals("動詞")?View.VISIBLE:View.GONE);
        pos.setOnItemClickListener((parent,view,index,id)->updateInflection.run());
        posBox.setHelperText("動詞・形容詞は終止形で登録（例：ぐぐる／ググる、動詞ラ五）");
        TextInputEditText comment=field(form,"コメント",original==null?"":original.comment());
        String[] kinds={"通常","除外（出さない）","抑制（優先度を下げる）"};
        var kind=selector(form,"候補の扱い",Arrays.asList(kinds),kinds[initial]);
        View kindBox=form.getChildAt(form.getChildCount()-1);form.removeView(kindBox);form.addView(kindBox,0);
        posBox.setEnabled(initial==0);updateInflection.run();
        kind.setOnItemClickListener((parent,view,index,id)->{posBox.setEnabled(index==0);updateInflection.run();});
        ScrollView editor=new ScrollView(this);editor.addView(form);
        MaterialAlertDialogBuilder builder=new MaterialAlertDialogBuilder(this).setTitle(original==null?"単語を追加":"単語を編集").setView(editor).setNegativeButton("取消",null).setPositiveButton("保存",null);
        if(original!=null)builder.setNeutralButton("削除",(dialog,which)->new MaterialAlertDialogBuilder(this).setTitle("「"+original.word()+"」を削除しますか？").setNegativeButton("取消",null).setPositiveButton("削除",(confirm,id)->saveEdit(original,null)).show());
        androidx.appcompat.app.AlertDialog dialog=builder.create();dialog.setOnShowListener(ignored->dialog.getButton(-1).setOnClickListener(view->{
            try{UserDictionary.Entry entry=new UserDictionary.Entry(reading.getText().toString(),word.getText().toString(),kind.getText().toString().equals(kinds[1])?"抑制単語":kind.getText().toString().equals(kinds[2])?"抑制":pos.getText().toString().equals("動詞")?verbs.get(conjugations.indexOf(inflection.getText().toString())):pos.getText().toString(),comment.getText().toString());saveEdit(original,entry);dialog.dismiss();}
            catch(IllegalArgumentException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        }));dialog.show();
    }
    private void saveEdit(UserDictionary.Entry original,UserDictionary.Entry replacement){
        run(()->{
            UserDictionaryStore store=UserDictionaryStore.get(this);List<UserDictionary.Entry> entries=new ArrayList<>(store.load().entries());
            int index=original==null?-1:entries.indexOf(original);if(index>=0)entries.remove(index);
            if(replacement!=null)entries.add(index>=0?index:entries.size(),replacement);
            UserDictionary changed=new UserDictionary(entries);store.save(changed);return changed;
        },replacement==null?"削除しました":"保存しました");
    }
    private void run(Callable<UserDictionary> action,String success){
        if(busy)return;busy=true;render();
        worker.submit(()->{
            try{UserDictionary result=action.call();runOnUiThread(()->{if(isDestroyed())return;dictionary=result;busy=false;render();if(success!=null)Toast.makeText(this,success,Toast.LENGTH_SHORT).show();});}
            catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;busy=false;render();new MaterialAlertDialogBuilder(this).setTitle("辞書を処理できませんでした").setMessage(e.getMessage()).setPositiveButton("閉じる",null).show();});}
        });
    }
}
