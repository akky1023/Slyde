package net.neomenia.glideprototype;

import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import net.neomenia.inputcore.Romanizer;
import net.neomenia.inputcore.JapaneseTypography;
import net.neomenia.inputcore.ZenzCandidates;
import net.neomenia.inputcore.CandidateResults;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Input and conversion session for the system IME. */
final class KeyboardSession extends ContextWrapper implements GlideKeyboardView.Listener {
    private TextView route,status;
    private CandidateBarView candidates;
    private boolean candidateExpanded=false;
    private int candidateRequest=0;
    private GlideKeyboardView keyboard;
    private FrameLayout keyboardArea;
    private JapaneseTypography typography;
    private ZenzRuntime zenz;
    private String zenzState="準備中";
    private InputConnection connection;
    private EditorInfo editorInfo;
    private boolean privateField;
    private final StringBuilder composing=new StringBuilder();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(task->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);task.run();},"dictionary"));
    private Future<?> dictionaryTask;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile SumireAdapter engine;
    private volatile int generation=0;
    private volatile boolean destroyed=false;
    private final Runnable autoCandidates=()->convert(true);
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}


    private final LinearLayout view;
    private final KeyboardDockView dock;
    private int dockHeight;
    KeyboardSession(Context context,TextView routeLabel,TextView statusLabel){
        super(context);this.route=routeLabel==null?new TextView(this):routeLabel;this.status=statusLabel==null?new TextView(this):statusLabel;
        view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);view.setBackgroundColor(Color.rgb(21,30,39));
        candidates=new CandidateBarView(this,this::chooseCandidate,this::setCandidateExpanded,this::settingsMenu);view.addView(candidates,new LinearLayout.LayoutParams(-1,candidateHeight()));renderCandidates();
        keyboardArea=new FrameLayout(this);view.addView(keyboardArea);
        keyboard=new GlideKeyboardView(this,this);keyboard.setNaNyaBias(getPreferences(0).getInt("naNyaBias",50));keyboardArea.addView(keyboard);
        keyboard.setVowelPairs(getPreferences(0).getBoolean("enableEu",true),getPreferences(0).getBoolean("enableIu",true));
        loadTypography();applyImeFont();
        for(int board=0;board<2;board++)keyboard.setSymbolBoard(board,SymbolBoards.load(getPreferences(0),board),SymbolBoards.returns(getPreferences(0),board));
        dock=new KeyboardDockView(this,view,getPreferences(0),candidateHeight(),this::applyDockHeight,keyboard::setSideWidths);
        zenz=ZenzRuntime.get(this);if(getPreferences(0).getBoolean("zenzEnabled",true))warmupZenz();
        worker.execute(()->{
            try {SumireAdapter loaded=SumireAdapter.get(this);engine=loaded;main.post(()->{if(!destroyed){status.setText(inputStatus());scheduleCandidates();}});}
            catch(Exception|OutOfMemoryError e){main.post(()->{if(!destroyed)status.setText("変換の準備に失敗しました："+e.getClass().getSimpleName()+" / "+e.getMessage());});}
        });
    }
    View view(){return dock;}
    private void applyDockHeight(int height){
        dockHeight=height;int keys=Math.max(dp(96),height-candidateHeight());
        candidates.setKeyboardHeight(keys);
        int area=candidates.getVisibility()==View.GONE?height:keys;
        if(keyboardArea.getLayoutParams().height!=area){keyboardArea.getLayoutParams().height=area;}
    }
    boolean cancelLayoutEdit(){if(!dock.isEditing())return false;dock.cancelEdit();return true;}
    private void settingsMenu(){
        ImeSettingsMenu.show(this,candidates.settingsAnchor(),this::editLayout,()->startActivity(new Intent(this,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
    }
    private void editLayout(){commit();keyboard.cancelTouches();dock.edit();}
    private SharedPreferences getPreferences(int mode){return InputPreferences.get(this);}
    void start(InputConnection input,EditorInfo info){
        generation++;clearCandidates();composing.setLength(0);connection=input;editorInfo=info;
        privateField=net.neomenia.inputcore.EditorPolicy.isPassword(info.inputType);
        reloadSettings();
        keyboard.resetMode(net.neomenia.inputcore.EditorPolicy.prefersLatin(info.inputType));
        if(net.neomenia.inputcore.EditorPolicy.prefersNumbers(info.inputType))keyboard.showNumbers();
    }
    void reloadSettings(){
        keyboard.setNaNyaBias(getPreferences(0).getInt("naNyaBias",50));
        keyboard.setVowelPairs(getPreferences(0).getBoolean("enableEu",true),getPreferences(0).getBoolean("enableIu",true));
        loadTypography();applyImeFont();renderCandidates();
        dock.reloadPreferences();
        for(int board=0;board<2;board++)keyboard.setSymbolBoard(board,SymbolBoards.load(getPreferences(0),board),SymbolBoards.returns(getPreferences(0),board));
    }
    void finish(){
        dock.cancelEdit();keyboard.cancelTouches();commit();
        if(connection!=null)connection.finishComposingText();connection=null;
    }
    void selectionChanged(int start,int end,int composingEnd){
        if(composing.length()>0&&(start!=composingEnd||end!=composingEnd)){
            composing.setLength(0);generation++;clearCandidates();if(connection!=null)connection.finishComposingText();
        }
    }
    private void sendKey(int code){connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));}
    static void enter(InputConnection connection,EditorInfo editorInfo){
        // The editor's newline request also takes precedence over custom actions.
        if(editorInfo!=null&&(editorInfo.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)==0){
            if(editorInfo.actionLabel!=null&&editorInfo.actionId!=0){connection.performEditorAction(editorInfo.actionId);return;}
            int action=editorInfo.imeOptions&EditorInfo.IME_MASK_ACTION;
            if(action!=EditorInfo.IME_ACTION_NONE&&action!=EditorInfo.IME_ACTION_UNSPECIFIED){connection.performEditorAction(action);return;}
        }
        if(editorInfo!=null&&editorInfo.inputType==InputType.TYPE_NULL){
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));
        }else connection.commitText("\n",1);
    }
    void destroy(){dock.cancelEdit();keyboard.cancelTouches();destroyed=true;generation++;if(zenz!=null)zenz.cancel();worker.shutdownNow();main.removeCallbacksAndMessages(null);connection=null;}
    @Override public void text(String raw,boolean japanese){
        if(connection==null)return;
        generation++;invalidateCandidates();
        if(japanese&&!privateField){String visible=Romanizer.append(composing.toString(),raw);composing.setLength(0);composing.append(visible);connection.setComposingText(typography.format(visible),1);scheduleCandidates();}
        else{commit();connection.commitText(raw,1);}
    }
    private String reading(){return typography.format(Romanizer.convert(composing.toString(),true));}
    private void loadTypography(){
        typography=new JapaneseTypography(getPreferences(0).getBoolean("commaStyle",false),getPreferences(0).getBoolean("periodStyle",false),getPreferences(0).getBoolean("openParenStyle",false),getPreferences(0).getBoolean("closeParenStyle",false));keyboard.setTypography(typography);
    }
    private void applyImeFont(){
        android.graphics.Typeface font=ImeFonts.load(this,Math.max(0,Math.min(2,getPreferences(0).getInt("imeFont",0))));keyboard.setTypeface(font);candidates.setTypeface(font);candidates.setTextSp(getPreferences(0).getInt("candidateTextSp",18));
        boolean badges=getPreferences(0).getBoolean("showCharacterBadges",true),ai=getPreferences(0).getBoolean("showAiBadges",true);
        candidates.setAnnotationVisibility(badges,ai);
    }
    private void warmupZenz(){
        zenz.warmup(error->{if(destroyed||!getPreferences(0).getBoolean("zenzEnabled",true))return;zenzState=error==null?"有効":"利用不可："+error;status.setText(inputStatus());});
    }
    private String inputStatus(){return "Sumire基本辞書 · "+(getPreferences(0).getBoolean("zenzEnabled",true)?"zenz "+zenzState+" · ":"")+"オフライン変換";}
    void commit(){generation++;if(connection==null){composing.setLength(0);clearCandidates();return;}if(composing.length()>0){connection.commitText(reading(),1);composing.setLength(0);}connection.finishComposingText();clearCandidates();}
    private int candidateRows(){return Math.max(1,Math.min(3,getPreferences(0).getInt("candidateRows",1)));}
    private int candidateHeight(){return candidateRows()*candidates.rowHeight();}
    private void invalidateCandidates(){
        if(zenz!=null)zenz.cancel();
        if(dictionaryTask!=null){dictionaryTask.cancel(false);dictionaryTask=null;}
        main.removeCallbacks(autoCandidates);if(candidates!=null)setCandidateExpanded(false);
    }
    private void clearCandidates(){
        invalidateCandidates();if(candidates!=null)candidates.setCandidates(List.of());
    }
    private void scheduleCandidates(){
        main.removeCallbacks(autoCandidates);if(!destroyed&&composing.length()>0)main.post(autoCandidates);
    }
    private void renderCandidates(){
        candidates.setRows(candidateRows());candidates.getLayoutParams().height=candidateHeight();candidates.requestLayout();
        if(dock!=null){dock.setCandidateHeight(candidateHeight());if(dockHeight>0)applyDockHeight(dockHeight);}
    }
    private void chooseCandidate(String choice){
        if(connection==null||candidateRequest!=generation)return;generation++;connection.commitText(choice,1);composing.setLength(0);clearCandidates();status.setText(inputStatus());
    }
    private void setCandidateExpanded(boolean expanded){
        candidates.setExpanded(expanded);
        if(!expanded&&!candidateExpanded)return;
        candidateExpanded=expanded;keyboardArea.setVisibility(expanded?View.GONE:View.VISIBLE);
    }
    @Override public void action(String action){
        if(connection==null)return;
        switch(action){
            case "commit" -> commit();
            case "delete" -> {
                generation++;invalidateCandidates();
                if(composing.length()>0){String visible=typography.format(Romanizer.convert(composing.toString(),false));int end=Character.offsetByCodePoints(visible,visible.length(),-1);composing.setLength(0);composing.append(visible,0,end);connection.setComposingText(composing.toString(),1);}
                else if(connection.getSelectedText(0)!=null)connection.commitText("",1);else if(!connection.deleteSurroundingTextInCodePoints(1,0))sendKey(KeyEvent.KEYCODE_DEL);
                if(composing.length()>0)scheduleCandidates();else clearCandidates();
            }
            case "space" -> {if(keyboard.isJapanese()&&composing.length()>0)convert();else{commit();connection.commitText(" ",1);}}
            case "enter" -> {if(composing.length()>0)commit();else enter(connection,editorInfo);}
            case "left","right" -> {commit();sendKey(action.equals("left")?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT);}
        }
    }
    @Override public void preview(String path,String result){route.setText(path.isEmpty()?"子音から母音へなぞって入力":path+"   →   "+result);}
    private void convert(){
        convert(false);
    }
    private void convert(boolean automatic){
        if(connection==null||privateField||composing.length()==0)return;
        invalidateCandidates();
        String input=reading();SumireAdapter loaded=engine;
        final int request=++generation;candidateRequest=request;if(!automatic)status.setText("変換中…");
        final JapaneseTypography style=typography;
        List<String> cached=loaded==null?null:loaded.cachedCandidates(input);
        final CandidateResults results=new CandidateResults(formatChoices(cached==null?List.of():cached,input,style),cached!=null&&!cached.isEmpty());
        candidates.setCandidates(results.values());
        final boolean useZenz=getPreferences(0).getBoolean("zenzEnabled",true)&&input.length()<=2048&&input.codePoints().anyMatch(cp->cp>='ぁ'&&cp<='ゖ');
        CharSequence preceding=useZenz?connection.getTextBeforeCursor(192,0):null;
        String before=preceding==null?"":preceding.toString();
        String visible=typography.format(Romanizer.convert(composing.toString(),false));
        if(before.endsWith(visible))before=before.substring(0,before.length()-visible.length());
        CharSequence following=useZenz?connection.getTextAfterCursor(64,0):null;
        String after=following==null?"":following.toString();
        final String left=before.substring(before.offsetByCodePoints(before.length(),-Math.min(64,before.codePointCount(0,before.length()))));
        final String right=after.substring(0,after.offsetByCodePoints(0,Math.min(32,after.codePointCount(0,after.length()))));
        if(useZenz)zenz.request(input,left,right,value->{
            if(destroyed||request!=generation||!getPreferences(0).getBoolean("zenzEnabled",true))return;
            String formattedAi=style.format(value);if(!ZenzCandidates.safe(formattedAi))return;
            results.ai(formattedAi);candidates.setCandidates(results.values(),results.ai());status.setText("Sumire基本辞書 + zenz · AI候補を追加");
        });
        if(loaded==null){if(!automatic)Toast.makeText(this,"辞書を準備しています",Toast.LENGTH_SHORT).show();return;}
        if(cached!=null){if(!automatic)status.setText(inputStatus());return;}
        dictionaryTask=worker.submit(()->{
            if(destroyed||request!=generation)return;
            List<String> found;String error=null;
            try{found=loaded.candidates(input,()->destroyed||request!=generation);}catch(Exception e){found=List.of();error=e.getMessage();}
            if(destroyed||request!=generation)return;
            List<String> choices=formatChoices(found,input,style);
            boolean dictionaryReady=!found.isEmpty();
            String finalError=error;
            main.post(()->{if(destroyed||request!=generation)return;
                if(!automatic||finalError!=null)status.setText(finalError==null?"Sumire基本辞書 · 候補を選んで確定":"変換エラー："+finalError);
                results.dictionary(choices,dictionaryReady);candidates.setCandidates(results.values(),results.ai());
            });
        });
    }
    private static List<String> formatChoices(List<String> values,String input,JapaneseTypography style){
        LinkedHashSet<String> choices=new LinkedHashSet<>();for(String value:values)choices.add(style.format(value));choices.add(input);choices.add(toKatakana(input));return new ArrayList<>(choices);
    }
    private static String toKatakana(String input){StringBuilder out=new StringBuilder();for(char c:input.toCharArray())out.append(c>='ぁ'&&c<='ゖ'?(char)(c+0x60):c);return out.toString();}
}
