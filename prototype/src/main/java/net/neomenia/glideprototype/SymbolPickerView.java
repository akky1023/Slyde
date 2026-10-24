package net.neomenia.glideprototype;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.*;
import android.widget.*;
import net.neomenia.inputcore.SymbolCatalog;
import net.neomenia.inputcore.SymbolPresentation;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.IOException;
import java.util.*;

/** Scrollable palette separate from the two editable twelve-key boards. */
final class SymbolPickerView extends LinearLayout {
    private static final int COLUMNS=6;
    private static final String DIVIDER="その他・低頻度";
    private final List<SymbolCatalog.Category> categories;
    private final List<SymbolCatalog.Entry> recent=new ArrayList<>();
    private final SharedPreferences preferences;
    private final SymbolPresentation presentation;
    private final GridView grid;
    private final TextView empty;
    private final FrameLayout content;
    private boolean categoryAnimating;
    private final HorizontalScrollView categoryScroll;
    private final LinearLayout categoryTabs;
    private final List<View> tabs=new ArrayList<>();
    private final Set<String> emojiValues=new HashSet<>();
    private final int[] selection={1,0};
    private final TextView symbolToggle,emojiToggle;
    private boolean emojiMode=false;
    private boolean showAnnotations=true;
    private Typeface typeface=Typeface.DEFAULT;
    private float swipeX,swipeY;
    private boolean swipeEligible,swiping;
    private final BaseAdapter adapter=new BaseAdapter(){
        public int getCount(){int start=separatorStart();return entries().size()+(start<0?0:start+COLUMNS-commonCount());}
        public Object getItem(int position){int index=entryIndex(position);return index<0?null:entries().get(index);}
        public long getItemId(int position){return position;}
        @Override public boolean areAllItemsEnabled(){return false;}
        @Override public boolean isEnabled(int position){return entryIndex(position)>=0;}
        @Override public int getViewTypeCount(){return 3;}
        @Override public int getItemViewType(int position){return entryIndex(position)>=0?0:position>=separatorStart()?1:2;}
        public View getView(int position,View reusable,ViewGroup parent){
            int index=entryIndex(position);
            if(index<0){
                boolean divider=position>=separatorStart();View space=reusable==null?new View(getContext()):reusable;
                space.setTag(divider?DIVIDER:null);space.setContentDescription(divider&&position==separatorStart()?DIVIDER:null);
                space.setImportantForAccessibility(divider&&position==separatorStart()?View.IMPORTANT_FOR_ACCESSIBILITY_YES:View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                space.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(divider?32:48)));return space;
            }
            WidthLabelView cell=reusable instanceof WidthLabelView?(WidthLabelView)reusable:new WidthLabelView(getContext());
            cell.useSymbolTypography();
            cell.setEmojiTypography(emojiMode);
            SymbolCatalog.Entry entry=entries().get(index);cell.setValue(entry.value());cell.setAnnotationVisibility(showAnnotations,false);cell.setContentDescription(entry.name()+(showAnnotations?" "+net.neomenia.inputcore.CharacterWidth.badge(entry.value()):""));cell.setTypeface(typeface);
            cell.setGravity(Gravity.CENTER);cell.setTextSize(25);cell.setTextColor(Color.WHITE);cell.setBackgroundColor(Color.rgb(34,47,59));
            cell.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(48)));return cell;
        }
    };
    SymbolPickerView(Context context,SharedPreferences prefs,GlideKeyboardView.Listener listener,Runnable back) throws IOException {
        super(context);preferences=prefs;presentation=SymbolPresentation.load();setOrientation(VERTICAL);setBackgroundColor(Color.rgb(21,30,39));
        categories=new ArrayList<>();categories.add(new SymbolCatalog.Category(SymbolCategoryPreferences.HISTORY,recent));categories.addAll(SymbolCatalog.load());
        for(int i=1;i<categories.size();i++)if(isEmoji(i)){
            if(selection[1]==0)selection[1]=i;
            for(var entry:categories.get(i).entries()){emojiValues.add(entry.value());emojiValues.add(entry.originalValue());}
        }
        try{JSONArray stored=new JSONArray(prefs.getString("recentSymbols","[]"));for(int i=0;i<Math.min(60,stored.length());i++){String value=stored.getString(i);if(!value.isEmpty())recent.add(new SymbolCatalog.Entry(value,value));}}catch(JSONException ignored){/* Ignore an invalid saved history. */}
        LinearLayout top=new LinearLayout(context);addView(top,new LayoutParams(-1,dp(48)));
        categoryScroll=new HorizontalScrollView(context);categoryScroll.setHorizontalScrollBarEnabled(true);categoryScroll.setContentDescription("カテゴリ（左右にスクロール）");
        categoryTabs=new LinearLayout(context);categoryScroll.addView(categoryTabs,new HorizontalScrollView.LayoutParams(-2,-1));
        top.addView(categoryScroll,new LayoutParams(0,-1,1));
        top.addView(UiIcons.button(context,R.drawable.ic_delete,"削除",()->listener.action("delete")),new LayoutParams(dp(56),-1));
        grid=new GridView(context){
            private final Paint dividerPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void dispatchDraw(Canvas canvas){
                super.dispatchDraw(canvas);
                for(int i=0;i<getChildCount();i++)if(DIVIDER.equals(getChildAt(i).getTag())){
                    View row=getChildAt(i);float y=row.getTop()+row.getHeight()/2f;
                    dividerPaint.setTypeface(Typeface.DEFAULT);dividerPaint.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
                    dividerPaint.setColor(Color.rgb(160,185,199));dividerPaint.setStrokeWidth(dp(1));
                    float half=dividerPaint.measureText(DIVIDER)/2f,center=getWidth()/2f;
                    canvas.drawLine(dp(8),y,center-half-dp(10),y,dividerPaint);canvas.drawLine(center+half+dp(10),y,getWidth()-dp(8),y,dividerPaint);
                    dividerPaint.setTextAlign(Paint.Align.CENTER);canvas.drawText(DIVIDER,center,y-(dividerPaint.ascent()+dividerPaint.descent())/2f,dividerPaint);break;
                }
            }
        };grid.setNumColumns(COLUMNS);grid.setVerticalSpacing(dp(3));grid.setHorizontalSpacing(dp(3));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setAdapter(adapter);
        empty=new TextView(context);empty.setTextColor(Color.WHITE);empty.setGravity(Gravity.CENTER);
        content=new FrameLayout(context);content.addView(grid,new FrameLayout.LayoutParams(-1,-1));content.addView(empty,new FrameLayout.LayoutParams(-1,-1));grid.setEmptyView(empty);
        addView(content,new LayoutParams(-1,0,1));
        grid.setOnItemClickListener((parent,view,position,id)->{
            int index=entryIndex(position);if(index<0)return;
            SymbolCatalog.Entry entry=entries().get(index);String value=presentation.apply(entry.value(),emojiMode);
            listener.text(value,false);listener.preview(emojiMode?"絵文字":"記号",value);
            if(privateMode)return;
            recent.removeIf(item->item.value().equals(entry.value())||item.value().equals(value));recent.add(0,new SymbolCatalog.Entry(value,entry.name(),entry.originalValue()));if(recent.size()>60)recent.remove(recent.size()-1);
            JSONArray saved=new JSONArray();for(var item:recent)saved.put(item.value());preferences.edit().putString("recentSymbols",saved.toString()).apply();
            if(selected()==0)adapter.notifyDataSetChanged();
        });
        grid.setOnItemLongClickListener((parent,view,position,id)->{int index=entryIndex(position);if(index<0)return false;Toast.makeText(context,entries().get(index).name(),Toast.LENGTH_SHORT).show();return true;});
        LinearLayout controls=new LinearLayout(context);addView(controls,new LayoutParams(-1,dp(48)));
        controls.addView(control("戻る","元のキーボードに戻る",back),new LayoutParams(dp(64),-1));
        symbolToggle=control("記号","記号に切り替え",()->switchMode(false));controls.addView(symbolToggle,new LayoutParams(0,-1,1));
        emojiToggle=control("絵文字","絵文字に切り替え",()->switchMode(true));controls.addView(emojiToggle,new LayoutParams(0,-1,1));
        controls.addView(UiIcons.button(context,R.drawable.ic_enter,"エンター",()->listener.action("enter")),new LayoutParams(dp(56),-1));
        rebuildCategories();
    }
    private boolean privateMode;
    void setPrivateMode(boolean value){privateMode=value;}
    private boolean isEmoji(int index){return categories.get(index).name().startsWith("絵文字：");}
    private int selected(){return selection[emojiMode?1:0];}
    private List<SymbolCatalog.Entry> entries(){
        if(selected()<0)return Collections.emptyList();
        if(selected()!=0)return categories.get(selected()).entries();
        List<SymbolCatalog.Entry> filtered=new ArrayList<>();for(var entry:recent)if(emojiValues.contains(entry.value())==emojiMode)filtered.add(entry);return filtered;
    }
    private int commonCount(){return selected()<=0?entries().size():categories.get(selected()).commonCount();}
    private int separatorStart(){int common=commonCount();return common>=entries().size()?-1:common+(COLUMNS-common%COLUMNS)%COLUMNS;}
    private int entryIndex(int position){
        if(position<0||position>=adapter.getCount())return -1;
        int separator=separatorStart();
        if(separator>=0&&position>=commonCount())return position<separator+COLUMNS?-1:position-(separator+COLUMNS-commonCount());
        return position;
    }
    private void switchMode(boolean emoji){if(emojiMode!=emoji){emojiMode=emoji;rebuildCategories();}}
    void setTypeface(Typeface value){typeface=value;applyFont(this,value);adapter.notifyDataSetChanged();}
    void setAnnotationVisibility(boolean value){showAnnotations=value;adapter.notifyDataSetChanged();}
    private void applyFont(View view,Typeface font){if(view instanceof TextView text)ImeFonts.apply(text,font);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)applyFont(group.getChildAt(i),font);}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            swipeX=event.getX();swipeY=event.getY();swiping=false;
            swipeEligible=swipeY>=content.getTop()&&swipeY<content.getBottom();
        }
        if(event.getPointerCount()>1)swipeEligible=false;
        float dx=event.getX()-swipeX,dy=event.getY()-swipeY;
        if((action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP)&&swipeEligible&&!swiping){
            if(Math.abs(dy)>dp(20)&&Math.abs(dy)>Math.abs(dx))swipeEligible=false;
            else if(Math.abs(dx)>dp(32)&&Math.abs(dx)>Math.abs(dy)*1.5f){
                swiping=true;MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();
            }
        }
        if(swiping){
            if(action==MotionEvent.ACTION_UP){if(Math.abs(dx)>dp(32))stepCategory(dx<0?1:-1);swiping=false;swipeEligible=false;}
            if(action==MotionEvent.ACTION_CANCEL){swiping=false;swipeEligible=false;}
            return true;
        }
        return super.dispatchTouchEvent(event);
    }
    private void stepCategory(int direction){
        if(categoryAnimating)return;
        for(int i=0;i<tabs.size();i++)if(((Integer)tabs.get(i).getTag())==selected()){
            int next=Math.max(0,Math.min(tabs.size()-1,i+direction));if(next==i)return;
            int target=(Integer)tabs.get(next).getTag();
            if(!android.animation.ValueAnimator.areAnimatorsEnabled()||content.getWidth()==0){selectCategory(target);return;}
            categoryAnimating=true;
            content.animate().translationX(-direction*content.getWidth()*.2f).alpha(0f).setDuration(100).withEndAction(()->{
                selectCategory(target);content.setTranslationX(direction*content.getWidth()*.2f);
                content.animate().translationX(0f).alpha(1f).setDuration(140).withEndAction(()->categoryAnimating=false).start();
            }).start();return;
        }
    }
    private void selectCategory(int index){selection[emojiMode?1:0]=index;refreshSelection();announceForAccessibility(categories.get(selected()).name());}
    private void cancelCategoryAnimation(){content.animate().cancel();content.setTranslationX(0f);content.setAlpha(1f);categoryAnimating=false;}
    @Override protected void onDetachedFromWindow(){cancelCategoryAnimation();super.onDetachedFromWindow();}
    private void rebuildCategories(){
        cancelCategoryAnimation();
        categoryTabs.removeAllViews();tabs.clear();
        for(int index=0;index<categories.size();index++){
            if(index!=0&&isEmoji(index)!=emojiMode)continue;
            if(!SymbolCategoryPreferences.enabled(preferences,categories.get(index).name()))continue;
            final int selectedIndex=index;String name=index==0?"履歴":categories.get(index).name().replace("絵文字：","");
            TextView tab=control(name,name+"のカテゴリ",()->{cancelCategoryAnimation();selectCategory(selectedIndex);});
            ImeFonts.apply(tab,typeface);tab.setPadding(dp(12),0,dp(12),0);
            tab.setTag(index);categoryTabs.addView(tab,new LayoutParams(-2,-1));tabs.add(tab);
        }
        boolean found=false;for(View tab:tabs)if((Integer)tab.getTag()==selected())found=true;
        if(!found)selection[emojiMode?1:0]=tabs.isEmpty()?-1:(Integer)tabs.get(0).getTag();
        refreshSelection();
    }
    void reloadCategories(){rebuildCategories();}
    private void refreshSelection(){
        for(View tab:tabs){
            boolean active=((Integer)tab.getTag())==selected();markSelected(tab,active);
            if(active)categoryScroll.post(()->{if(tab.getParent()==categoryTabs)categoryScroll.smoothScrollTo(Math.max(0,tab.getLeft()-(categoryScroll.getWidth()-tab.getWidth())/2),0);});
        }
        markSelected(symbolToggle,!emojiMode);markSelected(emojiToggle,emojiMode);
        empty.setText(selected()<0?"表示するカテゴリがありません。設定の「入力」→「総合記号ボード」で有効にしてください。":"使った記号がここに表示されます");
        adapter.notifyDataSetChanged();grid.setSelection(0);
    }
    private void markSelected(View view,boolean selected){
        view.setSelected(selected);int color=selected?Color.rgb(112,222,208):Color.WHITE;
        if(view instanceof TextView text)text.setTextColor(color);if(view instanceof ImageView image)image.setColorFilter(color);
        view.setBackgroundColor(selected?Color.rgb(43,73,88):Color.rgb(34,47,59));
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView control(String label,String description,Runnable action){
        Button button=new Button(getContext());button.setText(label);button.setContentDescription(description);button.setTextSize(14);button.setAllCaps(false);button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(0,0,0,0);button.setOnClickListener(view->action.run());button.setTextColor(Color.WHITE);button.setBackgroundColor(Color.rgb(34,47,59));return button;
    }
}
