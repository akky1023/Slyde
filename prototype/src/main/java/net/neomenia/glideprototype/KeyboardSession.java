package net.neomenia.glideprototype;

import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import net.neomenia.inputcore.Romanizer;
import net.neomenia.inputcore.JapaneseComposition;
import net.neomenia.inputcore.JapaneseTypography;
import net.neomenia.inputcore.ZenzCandidates;
import net.neomenia.inputcore.CandidateResults;
import net.neomenia.inputcore.ConversionForms;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Input and conversion session for the system IME. */
final class KeyboardSession extends ContextWrapper implements GlideKeyboardView.Listener {
    private TextView route,status;
    private CandidateBarView candidates;
    private boolean candidateExpanded=false;
    private int candidateRequest=0;
    private List<String> conversionChoices=List.of();
    private int conversionIndex=-1,pendingSpaceSteps=0;
    private boolean conversionChoicesReady=false;
    private GlideKeyboardView keyboard;
    private FrameLayout keyboardArea;
    private ImeSettingsMenu settings;
    private boolean settingsShowing;
    private JapaneseTypography typography;
    private ZenzRuntime zenz;
    private String zenzState="準備中";
    private InputConnection connection;
    private EditorInfo editorInfo;
    private boolean privateField;
    private final ConversionHistory conversionHistory=new ConversionHistory();
    private final JapaneseComposition composing=new JapaneseComposition();
    private int alphabetCase=-1;
    private String alphabetSource="";
    private final ExecutorService worker=Executors.newSingleThreadExecutor(task->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);task.run();},"dictionary"));
    private Future<?> dictionaryTask;
    private CandidateDisplayGate candidateDisplay;
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
        candidates=new CandidateBarView(this,this::chooseCandidate,this::setCandidateExpanded);view.addView(candidates,new LinearLayout.LayoutParams(-1,candidateHeight()));renderCandidates();
        keyboardArea=new FrameLayout(this);view.addView(keyboardArea);
        keyboard=new GlideKeyboardView(this,this);keyboard.setNaNyaBias(getPreferences(0).getInt("naNyaBias",50));keyboardArea.addView(keyboard);
        keyboard.setVowelPairs(getPreferences(0).getBoolean("enableEu",false),getPreferences(0).getBoolean("enableIu",false));
        loadTypography();applyImeFont();
        for(int board=0;board<2;board++)keyboard.setSymbolBoard(board,SymbolBoards.load(getPreferences(0),board),SymbolBoards.returns(getPreferences(0),board));
        dock=new KeyboardDockView(this,view,getPreferences(0),candidateHeight(),this::applyDockHeight,keyboard::setSideWidths);
        settings=new ImeSettingsMenu(this,this::closeSettings,()->{closeSettings();editLayout();},()->{
            closeSettings();startActivity(new Intent(this,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        settings.setVisibility(View.GONE);view.addView(settings,new LinearLayout.LayoutParams(-1,0));
        zenz=ZenzRuntime.get(this);if(getPreferences(0).getBoolean("zenzEnabled",true))warmupZenz();
        worker.execute(()->{
            try {SumireAdapter loaded=SumireAdapter.get(this);engine=loaded;main.post(()->{if(!destroyed){status.setText(inputStatus());scheduleCandidates();}});}
            catch(Exception|OutOfMemoryError e){main.post(()->{if(!destroyed)status.setText("変換の準備に失敗しました："+e.getClass().getSimpleName()+" / "+e.getMessage());});}
        });
    }
    View view(){return dock;}
    private void applyDockHeight(int height){
        if(settings!=null)settings.getLayoutParams().height=height;
        dockHeight=height;int keys=Math.max(dp(96),height-candidateHeight());
        candidates.setKeyboardHeight(keys);
        int area=candidates.getVisibility()==View.GONE?height:keys;
        if(keyboardArea.getLayoutParams().height!=area){keyboardArea.getLayoutParams().height=area;}
    }
    boolean cancelLayoutEdit(){if(settingsShowing){closeSettings();return true;}if(!dock.isEditing())return false;dock.cancelEdit();return true;}
    private void settingsMenu(){
        generation++;invalidateCandidates();keyboard.cancelTouches();settingsShowing=true;
        pendingSpaceSteps=0;if(conversionIndex>=0)candidateRequest=generation;
        candidates.setVisibility(View.GONE);keyboardArea.setVisibility(View.GONE);
        settings.getLayoutParams().height=dockHeight>0?dockHeight:view.getHeight();settings.setVisibility(View.VISIBLE);
    }
    private void closeSettings(){
        if(!settingsShowing)return;settingsShowing=false;settings.setVisibility(View.GONE);
        candidates.setVisibility(View.VISIBLE);keyboardArea.setVisibility(View.VISIBLE);
        if(dockHeight>0)applyDockHeight(dockHeight);view.requestLayout();scheduleCandidates();
    }
    private void editLayout(){commit();keyboard.cancelTouches();dock.edit();}
    private SharedPreferences getPreferences(int mode){return InputPreferences.get(this);}
    void start(InputConnection input,EditorInfo info){
        closeSettings();conversionHistory.start(info);
        generation++;clearCandidates();clearComposition();connection=input;editorInfo=info;
        privateField=net.neomenia.inputcore.EditorPolicy.isPassword(info.inputType);
        reloadSettings();
        keyboard.resetMode(new InputModeMemory(getPreferences(0)).startLatin(info.inputType,System.currentTimeMillis()));
        if(net.neomenia.inputcore.EditorPolicy.prefersNumbers(info.inputType))keyboard.showNumbers();
    }
    void reloadSettings(){
        keyboard.setNaNyaBias(getPreferences(0).getInt("naNyaBias",50));
        keyboard.setVowelPairs(getPreferences(0).getBoolean("enableEu",false),getPreferences(0).getBoolean("enableIu",false));
        loadTypography();applyImeFont();renderCandidates();
        if(connection!=null&&composing.length()>0&&alphabetCase<0&&conversionIndex<0)connection.setComposingText(composingDisplay(),1);
        dock.reloadPreferences();
        for(int board=0;board<2;board++)keyboard.setSymbolBoard(board,SymbolBoards.load(getPreferences(0),board),SymbolBoards.returns(getPreferences(0),board));
    }
    void finish(){
        if(connection!=null)new InputModeMemory(getPreferences(0)).hidden(System.currentTimeMillis());
        closeSettings();dock.cancelEdit();keyboard.cancelTouches();commit();
        if(connection!=null)connection.finishComposingText();connection=null;
    }
    void selectionChanged(int start,int end,int composingEnd){
        if(composing.length()>0&&(start!=composingEnd||end!=composingEnd)){
            clearComposition();generation++;clearCandidates();if(connection!=null)connection.finishComposingText();
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
    void destroy(){if(connection!=null)new InputModeMemory(getPreferences(0)).hidden(System.currentTimeMillis());dock.cancelEdit();keyboard.cancelTouches();conversionHistory.clear();destroyed=true;generation++;if(zenz!=null)zenz.cancel();worker.shutdownNow();main.removeCallbacksAndMessages(null);connection=null;}
    @Override public void modeChanged(boolean japanese){new InputModeMemory(getPreferences(0)).changed(japanese);}
    @Override public void text(String raw,boolean japanese){
        if(connection==null)return;
        InputConnection input=connection;
        // Deliver the alphabet commit and the next composing text as one editor update.
        input.beginBatchEdit();
        try{
            if(conversionIndex>=0){
                if(japanese&&!privateField&&getPreferences(0).getBoolean("cancelConversionOnInput",false))clearCandidates();
                else commit();
            }
            if(alphabetCase>=0){
                if(japanese&&!privateField&&getPreferences(0).getBoolean("cancelAlphabetOnInput",false)){
                    alphabetCase=-1;alphabetSource="";
                }else commit();
            }
            pendingSpaceSteps=0;generation++;invalidateCandidates();
            if(japanese&&!privateField){composing.append(raw);connection.setComposingText(composingDisplay(),1);scheduleCandidates();}
            else{commit();connection.commitText(raw,1);}
        }finally{input.endBatchEdit();}
    }
    private void clearComposition(){composing.clear();alphabetCase=-1;alphabetSource="";}
    private String composingDisplay(){
        return composingAlphabet(typography.format(composing.toString()));
    }
    private String composingAlphabet(String value){
        if(!getPreferences(0).getBoolean("fullWidthComposingAlphabet",false))return value;
        // Keep internal romanization and explicit alphabet conversion unchanged.
        StringBuilder display=new StringBuilder(value.length());
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            display.append((c>='a'&&c<='z')||(c>='A'&&c<='Z')?(char)(c+0xfee0):c);
        }
        return display.toString();
    }
    private String reading(){
        if(conversionIndex>=0)return conversionChoices.get(conversionIndex);
        if(alphabetCase>=0){
            String lower=alphabetSource.toLowerCase(Locale.ROOT);
            if(alphabetCase==2)return lower.toUpperCase(Locale.ROOT);
            if(alphabetCase==1&&!lower.isEmpty()){
                for(int i=0;i<lower.length();i++)if(Character.isLetter(lower.charAt(i)))
                    return lower.substring(0,i)+lower.substring(i,i+1).toUpperCase(Locale.ROOT)+lower.substring(i+1);
            }
            return lower;
        }
        return composingAlphabet(typography.format(Romanizer.convert(composing.toString(),true)));
    }
    private void alphabet(){
        if(!getPreferences(0).getBoolean("alphabetButtonEnabled",true))return;
        if(!keyboard.isJapanese()||privateField||composing.length()==0)return;
        if(alphabetCase<0)alphabetSource=composing.alphabet();
        alphabetCase=(alphabetCase+1)%3;generation++;clearCandidates();
        connection.setComposingText(reading(),1);
    }
    private void loadTypography(){
        boolean parentheses=InputPreferences.halfWidthParentheses(getPreferences(0));
        typography=new JapaneseTypography(getPreferences(0).getBoolean("commaStyle",false),getPreferences(0).getBoolean("periodStyle",false),parentheses,parentheses);keyboard.setTypography(typography);
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
    void commit(){generation++;if(connection==null){clearComposition();clearCandidates();return;}if(composing.length()>0){String value=reading();if(connection.commitText(value,1))conversionHistory.record(value);clearComposition();}connection.finishComposingText();clearCandidates();}
    private int candidateRows(){return Math.max(1,Math.min(3,getPreferences(0).getInt("candidateRows",1)));}
    private int candidateHeight(){return candidateRows()*candidates.rowHeight();}
    private void invalidateCandidates(){
        if(candidateDisplay!=null){candidateDisplay.cancel();candidateDisplay=null;}
        if(zenz!=null)zenz.cancel();
        if(dictionaryTask!=null){dictionaryTask.cancel(false);dictionaryTask=null;}
        main.removeCallbacks(autoCandidates);if(candidates!=null)setCandidateExpanded(false);
    }
    private void clearCandidates(){
        conversionChoices=List.of();conversionIndex=-1;pendingSpaceSteps=0;conversionChoicesReady=false;
        if(candidates!=null)candidates.setSelectedCandidate(-1);
        invalidateCandidates();if(candidates!=null)candidates.setCandidates(List.of());
    }
    private void spaceConversion(){
        if(!getPreferences(0).getBoolean("spaceCyclesCandidates",false)){convert();return;}
        if(conversionIndex>=0){selectConversion(1);return;}
        if(candidateRequest==generation&&conversionChoicesReady&&!conversionChoices.isEmpty()){selectConversion(1);return;}
        pendingSpaceSteps++;
        if(candidateDisplay==null)convert();
    }
    private void selectConversion(int steps){
        if(conversionChoices.isEmpty())return;
        conversionIndex=(int)(((long)conversionIndex+steps)%conversionChoices.size());
        // Freeze this list so late dictionary/AI results cannot change the selected text.
        generation++;invalidateCandidates();candidateRequest=generation;pendingSpaceSteps=0;
        candidates.setSelectedCandidate(conversionIndex);
        connection.setComposingText(reading(),1);
    }
    private void scheduleCandidates(){
        main.removeCallbacks(autoCandidates);if(!destroyed&&alphabetCase<0&&conversionIndex<0&&composing.length()>0)main.post(autoCandidates);
    }
    private void renderCandidates(){
        candidates.setRows(candidateRows());candidates.getLayoutParams().height=candidateHeight();candidates.requestLayout();
        if(dock!=null){dock.setCandidateHeight(candidateHeight());if(dockHeight>0)applyDockHeight(dockHeight);}
    }
    private void chooseCandidate(String choice){
        if(connection==null||candidateRequest!=generation)return;generation++;if(connection.commitText(choice,1))conversionHistory.record(choice);clearComposition();clearCandidates();status.setText(inputStatus());
    }
    private void setCandidateExpanded(boolean expanded){
        candidates.setExpanded(expanded);
        if(!expanded&&!candidateExpanded)return;
        candidateExpanded=expanded;keyboardArea.setVisibility(expanded||settingsShowing?View.GONE:View.VISIBLE);
    }
    @Override public void action(String action){
        if(action.equals("settings")){settingsMenu();return;}
        if(connection==null)return;
        switch(action){
            case "commit" -> commit();
            case "alphabet" -> alphabet();
            case "delete" -> {
                pendingSpaceSteps=0;
                if(conversionIndex>=0){clearCandidates();connection.setComposingText(composingDisplay(),1);scheduleCandidates();break;}
                generation++;invalidateCandidates();
                if(composing.length()>0){
                    if(alphabetCase>=0){
                        alphabetSource=alphabetSource.substring(0,alphabetSource.offsetByCodePoints(alphabetSource.length(),-1));
                        // Keep the kana restored on resumed input consistent with alphabet edits.
                        composing.clear();composing.append(alphabetSource);
                        if(alphabetSource.isEmpty())clearComposition();
                        connection.setComposingText(alphabetCase>=0?reading():"",1);
                    }else{composing.delete();connection.setComposingText(composingDisplay(),1);}
                }
                else if(connection.getSelectedText(0)!=null)connection.commitText("",1);else if(!connection.deleteSurroundingTextInCodePoints(1,0))sendKey(KeyEvent.KEYCODE_DEL);
                if(composing.length()>0)scheduleCandidates();else clearCandidates();
            }
            case "space" -> {if(keyboard.isJapanese()&&alphabetCase<0&&composing.length()>0)spaceConversion();else{commit();connection.commitText(" ",1);}}
            case "enter" -> {if(composing.length()>0)commit();else enter(connection,editorInfo);}
            case "left","right" -> {
                // Repeated cursor moves must not restart the editor's composition updates.
                if(composing.length()>0)commit();
                sendKey(action.equals("left")?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT);
            }
        }
    }
    @Override public void preview(String path,String result){route.setText(path.isEmpty()?"子音から母音へなぞって入力":path+"   →   "+result);}
    private void convert(){
        convert(false);
    }
    private void convert(boolean automatic){
        if(settingsShowing||alphabetCase>=0||conversionIndex>=0||connection==null||privateField||composing.length()==0)return;
        invalidateCandidates();
        String input=reading();SumireAdapter loaded=engine;
        final int request=++generation;candidateRequest=0;if(!automatic)status.setText("変換中…");
        final JapaneseTypography style=typography;
        List<String> cached=loaded==null?null:loaded.cachedCandidates(input);
        final CandidateResults results=new CandidateResults(formatChoices(cached==null?List.of():cached,input,style),cached!=null&&!cached.isEmpty());
        final boolean useZenz=getPreferences(0).getBoolean("zenzEnabled",true)&&input.length()<=2048&&input.codePoints().anyMatch(cp->cp>='ぁ'&&cp<='ゖ');
        final int waitMillis=CandidateDisplayGate.waitMillis(useZenz&&getPreferences(0).getBoolean("waitForZenz",false),getPreferences(0).getBoolean("variableZenzWait",false),input);
        final boolean[] firstDisplay={true};
        final boolean[] choicesReady={cached!=null&&!cached.isEmpty()};
        final CandidateDisplayGate display=new CandidateDisplayGate(main,waitMillis,()->{
            if(destroyed||request!=generation)return;
            candidateRequest=request;candidates.setCandidates(results.values(),results.ai(),firstDisplay[0]);firstDisplay[0]=false;
            conversionChoices=new ArrayList<>(results.values());
            conversionChoicesReady=choicesReady[0];
            if(pendingSpaceSteps>0&&conversionChoicesReady&&!conversionChoices.isEmpty())selectConversion(pendingSpaceSteps);
        });
        candidateDisplay=display;display.update();
        if(request!=generation)return;
        CharSequence preceding=useZenz?connection.getTextBeforeCursor(192,0):null;
        String before=preceding==null?"":preceding.toString();
        String visible=composingDisplay();
        if(before.endsWith(visible))before=before.substring(0,before.length()-visible.length());
        CharSequence following=useZenz?connection.getTextAfterCursor(64,0):null;
        String after=following==null?"":following.toString();
        final String left=conversionHistory.context(before);
        final String right=after.substring(0,after.offsetByCodePoints(0,Math.min(32,after.codePointCount(0,after.length()))));
        if(useZenz)zenz.request(input,left,right,value->{
            if(destroyed||request!=generation||!getPreferences(0).getBoolean("zenzEnabled",true))return;
            String formattedAi=style.format(value);if(!ZenzCandidates.safe(formattedAi))return;
            results.ai(formattedAi);display.update();status.setText("Sumire基本辞書 + zenz · AI候補を追加");
        },display::release);
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
                results.dictionary(choices,dictionaryReady);choicesReady[0]=true;display.update();
            });
        });
    }
    static List<String> formatChoices(List<String> values,String input,JapaneseTypography style){
        LinkedHashSet<String> choices=new LinkedHashSet<>();
        for(String value:values)choices.add(value.codePoints().anyMatch(Character::isLetterOrDigit)?style.format(value):value);
        for(String value:values){String full=ConversionForms.fullWidthNumber(value);if(!full.equals(value))choices.add(full);}
        choices.add(input);choices.add(ConversionForms.katakana(input));
        choices.add(ConversionForms.halfWidthKatakana(input));choices.add(ConversionForms.fullWidthNumber(input));
        return new ArrayList<>(choices);
    }
}
