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
    private List<UserDictionaryStore.Group> dictionaries=List.of();
    private String selectedId="default",documentDictionaryId,documentDictionaryName;
    private boolean detailsOpen;
    private MaterialToolbar toolbar;
    private TextInputEditText search;
    private View tableHeader;
    private FloatingActionButton add;
    private boolean addMenuOpen;
    private PopupWindow actionPopup;
    private androidx.appcompat.app.AlertDialog activeDialog;
    // Serialize document operations across activity recreation, including pending imports.
    private static final ExecutorService worker=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task,"user-dictionary");thread.setDaemon(true);return thread;});
    private boolean busy=true;
    private int pendingOperations;
    private String query="";
    private TextView count;
    private final ActivityResultLauncher<String[]> importer=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{
        if(uri==null)return;
        String name=documentDictionaryName;
        run(()->{
            UserDictionary imported;
            try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("ファイルを開けません");imported=UserDictionaryStore.readImport(in);}
            return UserDictionaryStore.get(this).create(name,imported);
        },"辞書を取り込みました");
    });
    private final ActivityResultLauncher<String> exporter=registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"),uri->{
        if(uri==null)return;
        String target=documentDictionaryId;
        run(()->{
            UserDictionary saved=UserDictionaryStore.get(this).group(target).dictionary();
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                if(out==null)throw new IOException("ファイルを開けません");
                try(Writer writer=new OutputStreamWriter(out,StandardCharsets.UTF_8)){saved.write(writer);}
            }return target;
        },"辞書を書き出しました");
    });
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        if(saved!=null){selectedId=saved.getString("dictionary", "default");documentDictionaryId=saved.getString("documentDictionary");documentDictionaryName=saved.getString("documentDictionaryName");query=saved.getString("query", "");detailsOpen=saved.getBoolean("detailsOpen");}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(SettingsUi.surface(this));setContentView(root);
        if(android.os.Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.ime());root.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});}
        toolbar=new MaterialToolbar(this);toolbar.setTitle("カスタム辞書");toolbar.setNavigationIcon(R.drawable.ic_back);toolbar.setNavigationIconTint(com.google.android.material.color.MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurface,0));toolbar.setNavigationOnClickListener(view->back());root.addView(toolbar);
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){back();}});
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);int margin=SettingsUi.dp(this,16);page.setPadding(margin,SettingsUi.dp(this,8),margin,margin);root.addView(page,new LinearLayout.LayoutParams(-1,0,1));
        search=new TextInputEditText(this);search.setHint("よみ・単語を検索");search.setSingleLine(true);search.setText(query);page.addView(search);
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();render();}public void afterTextChanged(android.text.Editable s){}});
        count=SettingsUi.text(this,"読み込み中…",14);count.setPadding(0,SettingsUi.dp(this,8),0,SettingsUi.dp(this,12));page.addView(count);
        FrameLayout content=new FrameLayout(this);page.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout table=new LinearLayout(this);table.setOrientation(LinearLayout.VERTICAL);content.addView(table,new FrameLayout.LayoutParams(-1,-1));
        tableHeader=tableRow(new String[]{"単語","ヨミ","品詞"},true);table.addView(tableHeader);
        ScrollView scroll=new ScrollView(this);table.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,0,0,SettingsUi.dp(this,96));scroll.addView(body);
        add=new FloatingActionButton(this);add.setImageResource(R.drawable.ic_dictionary_add);
        add.setOnClickListener(view->{if(!busy){if(detailsOpen)edit(null);else if(addMenuOpen&&actionPopup!=null)actionPopup.dismiss();else showAddMenu();}});
        FrameLayout.LayoutParams addParams=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);content.addView(add,addParams);
        busy=false;run(()->selectedId,null);
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putString("dictionary",selectedId);state.putString("documentDictionary",documentDictionaryId);state.putString("documentDictionaryName",documentDictionaryName);state.putString("query",query);state.putBoolean("detailsOpen",detailsOpen);
    }
    @Override protected void onDestroy(){if(actionPopup instanceof AddMenuPopup popup){popup.afterClose=null;popup.dismissImmediately();}else if(actionPopup!=null)actionPopup.dismiss();if(add.getDrawable() instanceof android.graphics.drawable.Animatable icon)icon.stop();if(activeDialog!=null)activeDialog.dismiss();super.onDestroy();}
    private void back(){
        if(activeDialog!=null&&activeDialog.isShowing()){cancelDialog(activeDialog);return;}
        if(!detailsOpen){finish();return;}
        detailsOpen=false;search.clearFocus();
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);render();
    }
    private void openDictionary(UserDictionaryStore.Group group){
        selectedId=group.id();dictionary=group.dictionary();detailsOpen=true;search.setText("");render();
    }
    private void showAddMenu(){
        showMenu(add,new String[]{"辞書を追加","辞書を取り込む"},new Runnable[]{()->nameDictionary(true,false),()->nameDictionary(true,true)},true);
    }
    private void setAddMenuOpen(boolean open){
        if(addMenuOpen==open)return;
        addMenuOpen=open;add.setContentDescription(open?"辞書の追加メニューを閉じる":"辞書を追加");
        add.setImageResource(open?R.drawable.avd_dictionary_add_to_close:R.drawable.avd_dictionary_close_to_add);
        if(add.getDrawable() instanceof android.graphics.drawable.Animatable icon)icon.start();
    }
    private final class AddMenuPopup extends PopupWindow {
        private final LinearLayout menu;
        private final int buttons;
        private boolean closing;
        private Runnable afterClose;
        AddMenuPopup(LinearLayout menu,int buttons){super(menu,SettingsUi.dp(UserDictionaryActivity.this,240),-2,true);this.menu=menu;this.buttons=buttons;}
        @Override public void dismiss(){
            if(closing)return;
            if(!isShowing()||isDestroyed()||isFinishing()||!menu.isLaidOut()){dismissImmediately();return;}
            closing=true;setAddMenuOpen(false);int[] origin=new int[2],menuLocation=new int[2];add.getLocationOnScreen(origin);menu.getLocationOnScreen(menuLocation);int[] remaining={buttons};
            for(int i=0;i<buttons;i++){
                View item=menu.getChildAt(i);item.setEnabled(false);item.animate().cancel();
                float x=origin[0]+add.getWidth()/2f-menuLocation[0]-item.getLeft()-item.getWidth()/2f;
                float y=origin[1]+add.getHeight()/2f-menuLocation[1]-item.getTop()-item.getHeight()/2f;
                item.animate().translationX(x).translationY(y).scaleX(.25f).scaleY(.25f).alpha(0).setStartDelay(i*25L).setDuration(160).setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator()).withEndAction(()->{if(--remaining[0]==0)dismissImmediately();}).start();
            }
        }
        void dismissImmediately(){
            for(int i=0;i<buttons;i++){View item=menu.getChildAt(i);if(item!=null){item.animate().withEndAction(null);item.animate().cancel();}}
            super.dismiss();
        }
    }
    private void showDictionaryMenu(View anchor,UserDictionaryStore.Group group){
        selectedId=group.id();
        showMenu(anchor,new String[]{"削除","リネーム","書き出し"},new Runnable[]{this::deleteDictionary,()->nameDictionary(false,false),()->{
            documentDictionaryId=group.id();exporter.launch(group.name().replaceAll("[\\\\/:*?\"<>|]","_")+".txt");
        }},false);
    }
    private void showMenu(View anchor,String[] labels,Runnable[] actions,boolean above){
        if(actionPopup instanceof AddMenuPopup previous)previous.dismissImmediately();else if(actionPopup!=null)actionPopup.dismiss();
        LinearLayout menu=new LinearLayout(this);menu.setOrientation(LinearLayout.VERTICAL);menu.setGravity(Gravity.END);int padding=SettingsUi.dp(this,8);menu.setPadding(above?0:padding,padding,above?0:padding,padding);
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(SettingsUi.surface(this));background.setCornerRadius(SettingsUi.dp(this,16));
        PopupWindow popup=above?new AddMenuPopup(menu,labels.length):new PopupWindow(menu,SettingsUi.dp(this,240),-2,true);actionPopup=popup;popup.setBackgroundDrawable(above?new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT):background);popup.setElevation(above?0:SettingsUi.dp(this,8));popup.setOutsideTouchable(true);
        if(above){popup.setAnimationStyle(0);popup.setOnDismissListener(()->{setAddMenuOpen(false);Runnable action=((AddMenuPopup)popup).afterClose;if(action!=null&&!isDestroyed()&&!isFinishing())action.run();});}
        for(int i=0;i<labels.length;i++){
            Runnable action=actions[i];var item=new com.google.android.material.button.MaterialButton(this,null,above?com.google.android.material.R.attr.materialButtonStyle:com.google.android.material.R.attr.borderlessButtonStyle);item.setText(labels[i]);item.setAllCaps(false);item.setGravity((above?Gravity.CENTER:Gravity.START)|Gravity.CENTER_VERTICAL);item.setMinHeight(SettingsUi.dp(this,48));
            if(above){item.setCornerRadius(SettingsUi.dp(this,16));item.setBackgroundTintList(add.getBackgroundTintList());item.setTextColor(com.google.android.material.color.MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnPrimaryContainer,0));item.setInsetTop(0);item.setInsetBottom(0);item.setPadding(SettingsUi.dp(this,24),0,SettingsUi.dp(this,24),0);item.setAlpha(0);}
            item.setOnClickListener(view->{if(popup instanceof AddMenuPopup animated){if(animated.closing)return;animated.afterClose=()->{if(!busy)action.run();};popup.dismiss();}else{popup.dismiss();if(!busy)action.run();}});LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(above?-2:-1,above?SettingsUi.dp(this,56):-2);params.gravity=Gravity.END;if(above&&i>0)params.topMargin=SettingsUi.dp(this,12);menu.addView(item,params);
        }
        if(above){
            // Keep the FAB's area inside the transparent window so the flight is not clipped.
            menu.setClipChildren(false);menu.setClipToPadding(false);
            View close=new View(this);close.setContentDescription("辞書の追加メニューを閉じる");close.setFocusable(true);close.setOnClickListener(view->popup.dismiss());menu.addView(close,new LinearLayout.LayoutParams(-1,anchor.getHeight()+SettingsUi.dp(this,8)));
        }
        menu.measure(View.MeasureSpec.makeMeasureSpec(SettingsUi.dp(this,240),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        if(above){
            setAddMenuOpen(true);
            menu.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
                @Override public boolean onPreDraw(){
                    menu.getViewTreeObserver().removeOnPreDrawListener(this);if(((AddMenuPopup)popup).closing)return true;int[] origin=new int[2];add.getLocationOnScreen(origin);
                    for(int i=0;i<labels.length;i++){
                        View item=menu.getChildAt(i);int[] location=new int[2];item.getLocationOnScreen(location);
                        item.setTranslationX(origin[0]+add.getWidth()/2f-location[0]-item.getWidth()/2f);item.setTranslationY(origin[1]+add.getHeight()/2f-location[1]-item.getHeight()/2f);item.setScaleX(.25f);item.setScaleY(.25f);
                        item.animate().translationX(0).translationY(0).scaleX(1).scaleY(1).alpha(1).setStartDelay((labels.length-1-i)*35L).setDuration(220).setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator()).start();
                    }
                    return true;
                }
            });
        }
        popup.showAsDropDown(anchor,0,above?padding-menu.getMeasuredHeight():0,Gravity.END);
    }
    private void nameDictionary(boolean create,boolean importing){
        String id=selectedId;LinearLayout form=dialogForm();
        String current=dictionaries.stream().filter(group->group.id().equals(id)).findFirst().orElseThrow().name();
        TextInputEditText name=field(form,"辞書名",create?"":current);
        var dialog=new MaterialAlertDialogBuilder(this).setTitle(importing?"取り込む辞書の名前":create?"辞書を追加":"辞書名を変更").setView(form).setNegativeButton("取消",null).setPositiveButton(importing?"ファイルを選択":"保存",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(-1).setOnClickListener(view->{
            String value=name.getText().toString().strip();
            if(value.isEmpty()||value.length()>100||value.codePoints().anyMatch(Character::isISOControl)){name.setError("辞書名は改行を含めず1〜100文字で入力してください");return;}
            if(dictionaries.stream().anyMatch(group->(create||!group.id().equals(id))&&group.name().equals(value))){name.setError("同じ名前の辞書があります");return;}
            if(importing){documentDictionaryName=value;importer.launch(new String[]{"*/*"});dialog.dismiss();return;}
            run(()->{var store=UserDictionaryStore.get(this);if(create)return store.create(value);store.rename(id,value);return id;},create?"辞書を作成しました":"辞書名を変更しました");dialog.dismiss();
        }));dialog.show();enableDialogBack(dialog);
    }
    private void enableDialogBack(androidx.appcompat.app.AlertDialog dialog){
        activeDialog=dialog;dialog.setCancelable(true);
        dialog.setOnKeyListener((ignored,keyCode,event)->handleDialogBack(event));
        if(android.os.Build.VERSION.SDK_INT>=33){
            android.window.OnBackInvokedCallback callback=()->cancelDialog(dialog);
            var dispatcher=dialog.getWindow().getOnBackInvokedDispatcher();dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY,callback);
            dialog.setOnDismissListener(ignored->{dispatcher.unregisterOnBackInvokedCallback(callback);if(activeDialog==dialog)activeDialog=null;});
        }else dialog.setOnDismissListener(ignored->{if(activeDialog==dialog)activeDialog=null;});
    }
    private LinearLayout dialogForm(){
        // Receive Back before the focused exposed dropdown or the IME consumes it.
        LinearLayout form=new LinearLayout(this){@Override public boolean dispatchKeyEventPreIme(android.view.KeyEvent event){return handleDialogBack(event)||super.dispatchKeyEventPreIme(event);}};
        form.setOrientation(LinearLayout.VERTICAL);int padding=SettingsUi.dp(this,20);form.setPadding(padding,0,padding,0);return form;
    }
    private boolean handleDialogBack(android.view.KeyEvent event){
        if(event.getKeyCode()!=android.view.KeyEvent.KEYCODE_BACK||activeDialog==null||!activeDialog.isShowing())return false;
        if(event.getAction()==android.view.KeyEvent.ACTION_UP&&!event.isCanceled())cancelDialog(activeDialog);return true;
    }
    private void cancelDialog(androidx.appcompat.app.AlertDialog dialog){
        View decor=dialog.getWindow().getDecorView();if(decor instanceof android.view.ViewGroup group)group.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        dismissDropdowns(decor);dialog.cancel();
    }
    private static void dismissDropdowns(View view){
        if(view instanceof AutoCompleteTextView dropdown)dropdown.dismissDropDown();
        if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)dismissDropdowns(group.getChildAt(i));
    }
    private void deleteDictionary(){
        if(dictionaries.size()==1){Toast.makeText(this,"最後の辞書は削除できません",Toast.LENGTH_SHORT).show();return;}
        String id=selectedId;String name=dictionaries.stream().filter(group->group.id().equals(id)).findFirst().orElseThrow().name();
        new MaterialAlertDialogBuilder(this).setTitle("「"+name+"」を削除しますか？").setMessage("この辞書に登録した単語も削除されます。").setNegativeButton("取消",null)
            .setPositiveButton("削除",(dialog,which)->run(()->{var store=UserDictionaryStore.get(this);store.delete(id);return store.groups().get(0).id();},"辞書を削除しました")).show();
    }
    private void render(){
        if(body==null)return;body.removeAllViews();if(count==null)return;
        add.setEnabled(!busy&&!dictionaries.isEmpty());add.setContentDescription(detailsOpen?"単語を追加":addMenuOpen?"辞書の追加メニューを閉じる":"辞書を追加");
        search.setVisibility(detailsOpen?View.VISIBLE:View.GONE);tableHeader.setVisibility(detailsOpen?View.VISIBLE:View.GONE);
        toolbar.setTitle(detailsOpen?dictionaries.stream().filter(group->group.id().equals(selectedId)).map(UserDictionaryStore.Group::name).findFirst().orElse("カスタム辞書"):"カスタム辞書");
        if(!detailsOpen){
            count.setText(busy?"処理中…":dictionaries.size()+"個の辞書");
            for(var group:dictionaries){
                LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setGravity(Gravity.CENTER_VERTICAL);int padding=SettingsUi.dp(this,20);row.setPadding(padding,padding,padding,padding);
                row.addView(SettingsUi.text(this,group.name(),16));row.addView(SettingsUi.text(this,group.dictionary().entries().size()+"件",14));
                android.util.TypedValue background=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,background,true);row.setBackgroundResource(background.resourceId);row.setFocusable(true);
                row.setOnClickListener(view->{if(!busy)openDictionary(group);});row.setOnLongClickListener(view->{if(!busy)showDictionaryMenu(row,group);return true;});body.addView(row,new LinearLayout.LayoutParams(-1,-2));
            }
            return;
        }
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
        LinearLayout form=dialogForm();
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
        String sourceId=selectedId;List<UserDictionaryStore.Group> destinations=dictionaries;
        List<String> names=new ArrayList<>();for(var group:destinations)names.add(group.name());
        String selectedName=destinations.stream().filter(group->group.id().equals(sourceId)).findFirst().orElseThrow().name();
        var destination=selector(form,"保存先の辞書",names,selectedName);
        String[] kinds={"通常","除外（出さない）","抑制（優先度を下げる）"};
        var kind=selector(form,"候補の扱い",Arrays.asList(kinds),kinds[initial]);
        View kindBox=form.getChildAt(form.getChildCount()-1);form.removeView(kindBox);form.addView(kindBox,0);
        posBox.setEnabled(initial==0);updateInflection.run();
        kind.setOnItemClickListener((parent,view,index,id)->{posBox.setEnabled(index==0);updateInflection.run();});
        ScrollView editor=new ScrollView(this);editor.addView(form);
        MaterialAlertDialogBuilder builder=new MaterialAlertDialogBuilder(this).setTitle(original==null?"単語を追加":"単語を編集").setView(editor).setNegativeButton("取消",null).setPositiveButton("保存",null);
        if(original!=null)builder.setNeutralButton("削除",(dialog,which)->new MaterialAlertDialogBuilder(this).setTitle("「"+original.word()+"」を削除しますか？").setNegativeButton("取消",null).setPositiveButton("削除",(confirm,id)->saveEdit(sourceId,sourceId,original,null)).show());
        androidx.appcompat.app.AlertDialog dialog=builder.create();dialog.setOnShowListener(ignored->dialog.getButton(-1).setOnClickListener(view->{
            try{UserDictionary.Entry entry=new UserDictionary.Entry(reading.getText().toString(),word.getText().toString(),kind.getText().toString().equals(kinds[1])?"抑制単語":kind.getText().toString().equals(kinds[2])?"抑制":pos.getText().toString().equals("動詞")?verbs.get(conjugations.indexOf(inflection.getText().toString())):pos.getText().toString(),comment.getText().toString());String target=destinations.get(names.indexOf(destination.getText().toString())).id();saveEdit(sourceId,target,original,entry);dialog.dismiss();}
            catch(IllegalArgumentException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        }));dialog.show();enableDialogBack(dialog);
    }
    private void saveEdit(String sourceId,String destinationId,UserDictionary.Entry original,UserDictionary.Entry replacement){
        run(()->{
            UserDictionaryStore.get(this).edit(sourceId,destinationId,original,replacement);return destinationId;
        },replacement==null?"削除しました":"保存しました");
    }
    private void run(Callable<String> action,String success){
        pendingOperations++;busy=true;render();
        worker.submit(()->{
            try{String requested=action.call();List<UserDictionaryStore.Group> result=UserDictionaryStore.get(this).groups();
                var selected=result.stream().filter(group->group.id().equals(requested)).findFirst().orElse(result.get(0));
                runOnUiThread(()->{if(isDestroyed())return;dictionaries=result;selectedId=selected.id();dictionary=selected.dictionary();
                    busy=--pendingOperations>0;render();if(success!=null)Toast.makeText(this,success,Toast.LENGTH_SHORT).show();});}
            catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;busy=--pendingOperations>0;render();new MaterialAlertDialogBuilder(this).setTitle("辞書を処理できませんでした").setMessage(e.getMessage()).setPositiveButton("閉じる",null).show();});}
        });
    }
}
