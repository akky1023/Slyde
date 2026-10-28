package net.neomenia.glideprototype;

import android.content.*;
import android.os.Bundle;
import android.text.InputType;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import java.io.*;
import java.util.function.IntConsumer;

/** Settings only; opening the app never starts the conversion engines. */
public final class SettingsActivity extends AppCompatActivity {
    private LinearLayout root,body;
    private MaterialToolbar toolbar;
    private String page="";
    private TextView inputStatus;
    private SharedPreferences preferences;
    private final androidx.activity.OnBackPressedCallback navigationBack=new androidx.activity.OnBackPressedCallback(false){@Override public void handleOnBackPressed(){show(page.startsWith("board")?"入力":"");}};
    private int dp(int value){return SettingsUi.dp(this,value);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);preferences=InputPreferences.get(this);
        getOnBackPressedDispatcher().addCallback(this,navigationBack);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(SettingsUi.surface(this));setContentView(root);
        if(android.os.Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());root.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        }
        toolbar=new MaterialToolbar(this);toolbar.setNavigationIconTint(com.google.android.material.color.MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurface,0));root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(64)));toolbar.setNavigationOnClickListener(view->show(page.startsWith("board")?"入力":""));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(8),dp(20),dp(24));scroll.addView(body);
        show(saved==null?getIntent().getStringExtra("page"):saved.getString("page",""));
    }
    @Override protected void onSaveInstanceState(Bundle saved){saved.putString("page",page);super.onSaveInstanceState(saved);}
    @Override protected void onResume(){super.onResume();if(page.isEmpty())refreshInputStatus();}
    private void show(String target){
        page=target==null?"":target;body.removeAllViews();inputStatus=null;
        navigationBack.setEnabled(!page.isEmpty());
        toolbar.setTitle(page.isEmpty()?"キーボード設定":page.startsWith("board")?"記号入力ボード"+(page.endsWith("0")?"1":"2"):page);
        toolbar.setNavigationIcon(page.isEmpty()?null:getDrawable(R.drawable.ic_back));
        if(page.isEmpty())home();else switch(page){case "入力"->input();case "変換"->conversion();case "表示"->display();case "このアプリについて"->about();case "board0"->symbolBoard(0);case "board1"->symbolBoard(1);default->show("");}
    }
    private void heading(String value){TextView text=SettingsUi.text(this,value,20);text.setPadding(0,dp(16),0,dp(8));body.addView(text);}
    private void description(String value){TextView text=SettingsUi.text(this,value,14);text.setPadding(0,dp(4),0,dp(12));body.addView(text);}
    private void link(String value,Runnable action){SettingsUi.addButton(body,value,action);}
    private void home(){
        heading(getString(R.string.app_name));inputStatus=SettingsUi.text(this,"",14);body.addView(inputStatus);refreshInputStatus();
        link("キーボードを有効にする",()->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        link("キーボードを切り替える",()->getSystemService(InputMethodManager.class).showInputMethodPicker());
        heading("設定");for(String name:new String[]{"入力","変換","表示","このアプリについて"})link(name,()->show(name));
    }
    private void refreshInputStatus(){
        if(inputStatus==null)return;String id=new ComponentName(this,GlideInputMethodService.class).flattenToShortString();
        boolean enabled=getSystemService(InputMethodManager.class).getEnabledInputMethodList().stream().anyMatch(ime->ime.getId().equals(id));
        boolean selected=id.equals(Settings.Secure.getString(getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD));
        inputStatus.setText(selected?"使用中です":enabled?"有効です。キーボードを切り替えてください。":"キーボードを有効にして切り替えてください。");
    }
    private MaterialSwitch toggle(String title,String key,boolean fallback){
        MaterialSwitch control=new MaterialSwitch(this);control.setText(title);control.setTextColor(com.google.android.material.color.MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurface,0));control.setMinHeight(dp(56));control.setChecked(preferences.getBoolean(key,fallback));
        control.setOnCheckedChangeListener((button,checked)->preferences.edit().putBoolean(key,checked).apply());body.addView(control,new LinearLayout.LayoutParams(-1,-2));
        return control;
    }
    private void slider(String name,int min,int max,int initial,String suffix,IntConsumer save){
        TextView label=SettingsUi.text(this,name+"（"+initial+suffix+"）",16);label.setPadding(0,dp(16),0,0);body.addView(label);
        Slider slider=new Slider(this);slider.setValueFrom(min);slider.setValueTo(max);slider.setStepSize(1);slider.setValue(Math.max(min,Math.min(max,initial)));slider.setContentDescription(name);
        slider.addOnChangeListener((view,value,user)->{label.setText(name+"（"+Math.round(value)+suffix+"）");if(user)save.accept(Math.round(value));});body.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));
    }
    private void input(){
        heading("入力モード");
        MaterialSwitch remember=toggle("日本語・アルファベットの入力モードを記憶する","rememberInputMode",false);
        MaterialSwitch reset=toggle("3分経過で日本語入力に戻す","resetInputModeAfterThreeMinutes",false);
        reset.setEnabled(remember.isChecked());
        remember.setOnCheckedChangeListener((button,checked)->{
            SharedPreferences.Editor editor=preferences.edit().putBoolean("rememberInputMode",checked);
            if(!checked)editor.remove("savedJapaneseInputMode").remove("inputModeHiddenAt");
            editor.apply();reset.setEnabled(checked);
        });
        // Saved bias increases toward NYA; reverse the UI so increasing it favors NA, preserving existing choices.
        heading("グライド入力");slider("NAの判定を出しやすくする",0,100,100-preferences.getInt("naNyaBias",50),"",value->preferences.edit().putInt("naNyaBias",100-value).apply());
        description("グライドの軌道がNYA側に曲がってもある程度NA判定が出るようにします．");toggle("XEUをグライド入力","enableEu",false);toggle("XIUをグライド入力","enableIu",false);
        description("子音から始まる -EU/-IU の形の連母音をグライド入力するかを設定します．基本的に日本語では低頻度で -U の誤入力が増えやすくなるためオフを推奨しています．");
        heading("句読点・括弧");punctuationDropdown();
        toggle("丸括弧を半角にする","halfWidthParentheses",InputPreferences.halfWidthParentheses(preferences));
        heading("記号入力ボード");link("記号入力ボード1",()->show("board0"));link("記号入力ボード2",()->show("board1"));
    }
    private void punctuationDropdown(){
        String[] styles={"、。","，．","，。","、．"};
        boolean comma=preferences.getBoolean("commaStyle",false),period=preferences.getBoolean("periodStyle",false);
        int current=comma?(period?1:2):(period?3:0);
        TextInputLayout box=new TextInputLayout(this,null,com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);box.setHint("句読点の種類");
        com.google.android.material.textfield.MaterialAutoCompleteTextView dropdown=new com.google.android.material.textfield.MaterialAutoCompleteTextView(box.getContext());
        dropdown.setInputType(InputType.TYPE_NULL);dropdown.setSimpleItems(styles);dropdown.setText(styles[current],false);
        dropdown.setOnItemClickListener((parent,view,index,id)->preferences.edit().putBoolean("commaStyle",index==1||index==2).putBoolean("periodStyle",index==1||index==3).apply());
        box.addView(dropdown,new LinearLayout.LayoutParams(-1,-2));body.addView(box,new LinearLayout.LayoutParams(-1,-2));
    }
    private void conversion(){
        heading("変換");
        MaterialSwitch cycle=toggle("空白キーで変換","spaceCyclesCandidates",false);
        MaterialSwitch cancelConversion=toggle("入力再開時に変換を取り消す","cancelConversionOnInput",false);
        cancelConversion.setEnabled(cycle.isChecked());
        cycle.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean("spaceCyclesCandidates",checked).apply();cancelConversion.setEnabled(checked);});
        heading("無変換");
        MaterialSwitch alphabet=toggle("無変換を有効にする","alphabetButtonEnabled",true);
        MaterialSwitch cancelAlphabet=toggle("入力再開時に無変換を取り消す","cancelAlphabetOnInput",false);
        cancelAlphabet.setEnabled(alphabet.isChecked());
        alphabet.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean("alphabetButtonEnabled",checked).apply();cancelAlphabet.setEnabled(checked);});
        heading("変換候補");slider("候補欄の行数",1,3,preferences.getInt("candidateRows",1),"行",value->preferences.edit().putInt("candidateRows",value).apply());
        toggle("zenzのオフラインAI変換を有効にする","zenzEnabled",true);
        MaterialSwitch wait=toggle("AI変換を待って候補を表示する","waitForZenz",false);
        RadioGroup modes=new RadioGroup(this);body.addView(modes,new LinearLayout.LayoutParams(-1,-2));
        RadioButton fixed=new RadioButton(this),variable=new RadioButton(this);
        fixed.setId(View.generateViewId());variable.setId(View.generateViewId());
        fixed.setText("固定：20ms");variable.setText("可変：20＋文字数×2 ms");
        modes.addView(fixed);modes.addView(variable);
        modes.check(preferences.getBoolean("variableZenzWait",false)?variable.getId():fixed.getId());
        fixed.setEnabled(wait.isChecked());variable.setEnabled(wait.isChecked());
        modes.setOnCheckedChangeListener((group,id)->preferences.edit().putBoolean("variableZenzWait",id==variable.getId()).apply());
        wait.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean("waitForZenz",checked).apply();fixed.setEnabled(checked);variable.setEnabled(checked);});
        description("比較的時間のかかるAI予測変換をある程度待つことで変換予測のちらつきを低減します．");
    }
    private void display(){
        heading("キーボード");link("大きさ・位置",()->startActivity(new Intent(this,LayoutSettingsActivity.class)));
        heading("フォント");String[] names=ImeFonts.NAMES;int current=Math.max(0,Math.min(2,preferences.getInt("imeFont",0)));
        TextView selected=SettingsUi.text(this,"現在のフォント："+names[current],14);body.addView(selected);
        link("フォントを選ぶ",()->new MaterialAlertDialogBuilder(this).setTitle("IMEのフォント").setSingleChoiceItems(names,Math.max(0,Math.min(2,preferences.getInt("imeFont",0))),(dialog,index)->{preferences.edit().putInt("imeFont",index).apply();selected.setText("現在のフォント："+names[index]);dialog.dismiss();}).setNegativeButton("取消",null).show());
        slider("変換候補の文字サイズ",12,30,preferences.getInt("candidateTextSp",18),"sp",value->preferences.edit().putInt("candidateTextSp",value).apply());
        toggle("紛らわしい文字の注記を表示（全・半・漢・カナなど）","showCharacterBadges",true);toggle("AI候補の「AI」を表示","showAiBadges",true);
    }
    private void about(){
        heading(getString(R.string.app_name));try{description("バージョン "+getPackageManager().getPackageInfo(getPackageName(),0).versionName);}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        description("12キーをなぞって入力する日本語キーボードです。辞書・AI変換は端末内で動作します。");
        link("リポジトリ",()->openUrl("https://github.com/akky1023/Slyde"));link("ライセンス",this::licenses);
        link("制作者情報",this::author);
    }
    private void openUrl(String url){startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}
    private void author(){
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER_HORIZONTAL);content.setPadding(dp(24),dp(24),dp(24),dp(8));
        androidx.core.graphics.drawable.RoundedBitmapDrawable portrait=androidx.core.graphics.drawable.RoundedBitmapDrawableFactory.create(getResources(),android.graphics.BitmapFactory.decodeResource(getResources(),R.drawable.author_avatar));portrait.setCircular(true);portrait.setAntiAlias(true);ImageView avatar=new ImageView(this);avatar.setImageDrawable(portrait);avatar.setContentDescription("akky_1023のプロフィール画像");avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);content.addView(avatar,new LinearLayout.LayoutParams(dp(120),dp(120)));
        android.text.SpannableString name=new android.text.SpannableString("akky_1023");
        name.setSpan(new android.text.style.URLSpan("https://neomenia.net"),0,name.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView label=SettingsUi.text(this,"",18);label.setText(name);label.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());label.setGravity(Gravity.CENTER);
        label.setLinkTextColor(com.google.android.material.color.MaterialColors.getColor(this,com.google.android.material.R.attr.colorPrimary,0));label.setPadding(0,dp(16),0,dp(12));content.addView(label,new LinearLayout.LayoutParams(-1,-2));
        androidx.appcompat.widget.AppCompatImageButton github=new androidx.appcompat.widget.AppCompatImageButton(this,null,android.R.attr.borderlessButtonStyle);github.setImageResource(R.drawable.ic_github);github.setImageTintList(android.content.res.ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurface,0)));github.setContentDescription("GitHub");github.setPadding(dp(12),dp(12),dp(12),dp(12));github.setScaleType(ImageView.ScaleType.FIT_CENTER);github.setOnClickListener(view->openUrl("https://github.com/akky1023"));content.addView(github,new LinearLayout.LayoutParams(dp(48),dp(48)));
        new MaterialAlertDialogBuilder(this).setView(content).setPositiveButton("閉じる",null).show();
    }
    private void licenses(){
        String[] groups={"Slyde本体","Mozc","Sumire","zenz"};
        new MaterialAlertDialogBuilder(this).setTitle("ライセンス").setItems(groups,(dialog,index)->licenseDetails(groups[index])).setNegativeButton("閉じる",null).show();
    }
    private void licenseDetails(String group){
        String summary;String[][] notices;
        switch(group){
            case "Slyde本体"->{
                summary="Slyde本体はMIT Licenseで公開しています。\n画面表示・フォント・記号名に使用する素材とライブラリの通知も掲載しています。";
                notices=new String[][]{{"Slyde本体 — MIT License","slyde-MIT.txt"},{"Material Components for Android / AndroidX — Apache License 2.0","zenz-model-Apache-2.0.txt"},{"GitHub Octicons — MIT License","github-octicons-MIT.txt"},{"Unicode","unicode.txt"},{"CLDR","cldr.txt"},{"Noto Sans JP — SIL Open Font License","NotoSansJP-OFL.txt"},{"Noto Sans JP — Notices","NotoSansJP-Notices.txt"},{"Bizin Gothic Discord — SIL Open Font License","BizinGothicDiscord-OFL.txt"},{"Bizin Gothic Discord — Notices","BizinGothicDiscord-Regular-Notices.txt"},{"Noto Sans Symbols — SIL Open Font License","NotoSymbols-OFL.txt"}};
            }
            case "Mozc"->{
                summary="読み付きの記号・絵文字辞書を使用しています。\nMozcのライセンスと第三者の通知を掲載しています。";
                notices=new String[][]{{"Mozc — Copyright / License / Third-party notices","mozc.txt"}};
            }
            case "Sumire"->{
                summary="Sumireの基本辞書とkotlin-kana-kanji-converterの変換処理を使用しています。\nコードのMIT Licenseと、辞書・データの個別の利用条件を掲載しています。";
                notices=new String[][]{{"Sumire — MIT License","sumire-MIT.txt"},{"kotlin-kana-kanji-converter — MIT License","sumire-converter-MIT.txt"},{"IPADIC — License / Notices","ipadic.txt"},{"IPADIC — COPYING","ipadic-copying.txt"},{"Data — License / Attribution","data.txt"},{"Mozc由来の辞書 — License / Notices","mozc.txt"}};
            }
            case "zenz"->{
                summary="端末内のAI変換にzenzを使用しています。\nJNIブリッジ・llama.cppはMIT License、zenzモデルはApache License 2.0です。";
                notices=new String[][]{{"Sumire JNI bridge — MIT License","zenz-bridge-MIT.txt"},{"llama.cpp — MIT License","llama-MIT.txt"},{"zenzモデル — Apache License 2.0","zenz-model-Apache-2.0.txt"}};
            }
            default->throw new IllegalArgumentException(group);
        }
        StringBuilder text=new StringBuilder(summary).append("\n\n");
        try{
            for(String[] notice:notices){text.append("■ ").append(notice[0]).append("\n\n");try(InputStream in=getAssets().open("licenses/"+notice[1])){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);text.append(out.toString("UTF-8")).append("\n\n");}}
            ScrollView scroll=new ScrollView(this);TextView content=SettingsUi.text(this,text.toString(),12);content.setTextIsSelectable(true);content.setPadding(dp(20),dp(8),dp(20),dp(8));scroll.addView(content);
            new MaterialAlertDialogBuilder(this).setTitle(group).setView(scroll).setNeutralButton("一覧に戻る",(dialog,which)->licenses()).setPositiveButton("閉じる",null).show();
        }catch(IOException error){Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void symbolBoard(int board){
        description("各キーに入力する記号を設定します。空欄のキーは数字配列への「戻る」になります。");
        String[] values=SymbolBoards.load(preferences,board);TextInputEditText[] fields=new TextInputEditText[12];
        for(int row=0;row<4;row++){
            LinearLayout line=new LinearLayout(this);body.addView(line,new LinearLayout.LayoutParams(-1,-2));
            for(int col=0;col<3;col++){
                int index=row*3+col;TextInputLayout box=new TextInputLayout(this,null,com.google.android.material.R.attr.textInputOutlinedStyle);box.setHint((row+1)+"行"+(col+1)+"列");
                TextInputEditText field=new TextInputEditText(box.getContext());field.setTextColor(com.google.android.material.color.MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurface,0));fields[index]=field;field.setSingleLine(true);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);field.setText(values[index]);field.setSelectAllOnFocus(true);box.addView(field,new LinearLayout.LayoutParams(-1,-2));
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(2),dp(6),dp(2),dp(6));line.addView(box,params);
            }
        }
        MaterialSwitch returnAfter=new MaterialSwitch(this);returnAfter.setText("記号入力後に数字配列へ戻る");returnAfter.setTextColor(com.google.android.material.color.MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurface,0));returnAfter.setChecked(SymbolBoards.returns(preferences,board));body.addView(returnAfter);
        link("初期配置に戻す",()->{String[] defaults=SymbolBoards.defaults(board);for(int i=0;i<12;i++)fields[i].setText(defaults[i]);returnAfter.setChecked(true);});
        link("保存",()->{String[] edited=new String[12];for(int i=0;i<12;i++)edited[i]=fields[i].getText().toString();SymbolBoards.save(preferences,board,edited,returnAfter.isChecked());show("入力");});
    }
}
