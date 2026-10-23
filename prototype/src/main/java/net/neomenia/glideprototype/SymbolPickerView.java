package net.neomenia.glideprototype;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import net.neomenia.inputcore.SymbolCatalog;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.IOException;
import java.util.*;

/** Scrollable palette separate from the two editable twelve-key boards. */
final class SymbolPickerView extends LinearLayout {
    private final List<SymbolCatalog.Category> categories;
    private final List<SymbolCatalog.Entry> recent=new ArrayList<>();
    private final SharedPreferences preferences;
    private final GridView grid;
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
        public int getCount(){return entries().size();}
        public Object getItem(int position){return entries().get(position);}
        public long getItemId(int position){return position;}
        public View getView(int position,View reusable,ViewGroup parent){
            WidthLabelView cell=reusable instanceof WidthLabelView?(WidthLabelView)reusable:new WidthLabelView(getContext());
            SymbolCatalog.Entry entry=entries().get(position);cell.setValue(entry.value());cell.setAnnotationVisibility(showAnnotations,false);cell.setContentDescription(entry.name()+(showAnnotations?" "+net.neomenia.inputcore.CharacterWidth.badge(entry.value()):""));cell.setTypeface(typeface);
            cell.setGravity(Gravity.CENTER);cell.setTextSize(25);cell.setTextColor(Color.WHITE);cell.setBackgroundColor(Color.rgb(34,47,59));
            cell.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(48)));return cell;
        }
    };
    SymbolPickerView(Context context,SharedPreferences prefs,GlideKeyboardView.Listener listener,Runnable back) throws IOException {
        super(context);preferences=prefs;setOrientation(VERTICAL);setBackgroundColor(Color.rgb(21,30,39));
        categories=new ArrayList<>();categories.add(new SymbolCatalog.Category("最近使った記号",recent));categories.addAll(SymbolCatalog.load());
        for(int i=1;i<categories.size();i++)if(isEmoji(i)){
            if(selection[1]==0)selection[1]=i;
            for(var entry:categories.get(i).entries())emojiValues.add(entry.value());
        }
        try{JSONArray stored=new JSONArray(prefs.getString("recentSymbols","[]"));for(int i=0;i<Math.min(60,stored.length());i++){String value=stored.getString(i);if(!value.isEmpty())recent.add(new SymbolCatalog.Entry(value,value));}}catch(JSONException ignored){/* Ignore an invalid saved history. */}
        LinearLayout top=new LinearLayout(context);addView(top,new LayoutParams(-1,dp(48)));
        categoryScroll=new HorizontalScrollView(context);categoryScroll.setHorizontalScrollBarEnabled(true);categoryScroll.setContentDescription("カテゴリ（左右にスクロール）");
        categoryTabs=new LinearLayout(context);categoryScroll.addView(categoryTabs,new HorizontalScrollView.LayoutParams(-2,-1));
        top.addView(categoryScroll,new LayoutParams(0,-1,1));
        top.addView(UiIcons.button(context,R.drawable.ic_delete,"削除",()->listener.action("delete")),new LayoutParams(dp(56),-1));
        grid=new GridView(context);grid.setNumColumns(6);grid.setVerticalSpacing(dp(3));grid.setHorizontalSpacing(dp(3));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setAdapter(adapter);
        TextView empty=new TextView(context);empty.setText("使った記号がここに表示されます");empty.setTextColor(Color.WHITE);empty.setGravity(Gravity.CENTER);
        FrameLayout content=new FrameLayout(context);content.addView(grid,new FrameLayout.LayoutParams(-1,-1));content.addView(empty,new FrameLayout.LayoutParams(-1,-1));grid.setEmptyView(empty);
        addView(content,new LayoutParams(-1,0,1));
        grid.setOnItemClickListener((parent,view,position,id)->{
            SymbolCatalog.Entry entry=entries().get(position);listener.text(entry.value(),false);listener.preview("記号",entry.value());
            if(privateMode)return;
            recent.removeIf(item->item.value().equals(entry.value()));recent.add(0,entry);if(recent.size()>60)recent.remove(recent.size()-1);
            JSONArray saved=new JSONArray();for(var item:recent)saved.put(item.value());preferences.edit().putString("recentSymbols",saved.toString()).apply();
            if(selected()==0)adapter.notifyDataSetChanged();
        });
        grid.setOnItemLongClickListener((parent,view,position,id)->{Toast.makeText(context,entries().get(position).name(),Toast.LENGTH_SHORT).show();return true;});
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
        if(selected()!=0)return categories.get(selected()).entries();
        List<SymbolCatalog.Entry> filtered=new ArrayList<>();for(var entry:recent)if(emojiValues.contains(entry.value())==emojiMode)filtered.add(entry);return filtered;
    }
    private void switchMode(boolean emoji){if(emojiMode!=emoji){emojiMode=emoji;rebuildCategories();}}
    void setTypeface(Typeface value){typeface=value;applyFont(this,value);adapter.notifyDataSetChanged();}
    void setAnnotationVisibility(boolean value){showAnnotations=value;adapter.notifyDataSetChanged();}
    private void applyFont(View view,Typeface font){if(view instanceof TextView text)text.setTypeface(font);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)applyFont(group.getChildAt(i),font);}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            swipeX=event.getX();swipeY=event.getY();swiping=false;
            swipeEligible=swipeY<getHeight()-dp(48)&&(swipeY>=dp(48)||swipeX<getWidth()-dp(56));
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
        for(int i=0;i<tabs.size();i++)if(((Integer)tabs.get(i).getTag())==selected()){
            int next=Math.max(0,Math.min(tabs.size()-1,i+direction));selection[emojiMode?1:0]=(Integer)tabs.get(next).getTag();refreshSelection();announceForAccessibility(categories.get(selected()).name());return;
        }
    }
    private void rebuildCategories(){
        categoryTabs.removeAllViews();tabs.clear();
        for(int index=0;index<categories.size();index++){
            if(index!=0&&isEmoji(index)!=emojiMode)continue;
            final int selectedIndex=index;String name=index==0?"履歴":categories.get(index).name().replace("絵文字：","");
            ImageButton tab=UiIcons.button(getContext(),UiIcons.category(categories.get(index).name()),name+"のカテゴリ",()->{selection[emojiMode?1:0]=selectedIndex;refreshSelection();});
            tab.setTag(index);categoryTabs.addView(tab,new LayoutParams(dp(48),-1));tabs.add(tab);
        }
        refreshSelection();
    }
    private void refreshSelection(){
        for(View tab:tabs){
            boolean active=((Integer)tab.getTag())==selected();markSelected(tab,active);
            if(active)categoryScroll.post(()->{if(tab.getParent()==categoryTabs)categoryScroll.smoothScrollTo(Math.max(0,tab.getLeft()-(categoryScroll.getWidth()-tab.getWidth())/2),0);});
        }
        markSelected(symbolToggle,!emojiMode);markSelected(emojiToggle,emojiMode);
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
