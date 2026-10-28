package net.neomenia.glideprototype;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import net.neomenia.inputcore.CandidateLayout;
import net.neomenia.inputcore.CharacterWidth;
import java.util.*;
import java.util.function.Consumer;

/** One candidate view shared by the compact strip and expanded list. */
final class CandidateBarView extends FrameLayout {
    private final FrameLayout strip;
    private final ImageButton expandButton;
    private final Consumer<String> select;
    private final Consumer<Boolean> expand;
    private final Flow flow;
    private int keyboardHeight=0;
    private List<String> choices=List.of();
    private String aiCandidate;
    private int selectedCandidate=-1;
    private int rows=1;
    private boolean expanded=false;
    private Typeface typeface=Typeface.DEFAULT;
    private int textSp=18;
    private boolean showBadges=true,showAi=true;

    CandidateBarView(Context context,Consumer<String> select,Consumer<Boolean> expand){
        super(context);this.select=select;this.expand=expand;setBackgroundColor(Color.rgb(28,40,51));
        setContentDescription("変換候補");
        strip=new FrameLayout(context);addView(strip,new LayoutParams(-1,-1));
        expandButton=UiIcons.button(context,R.drawable.ic_expand,"変換候補をすべて表示",()->expand.accept(!expanded));
        expandButton.setVisibility(INVISIBLE);
        addView(expandButton,new LayoutParams(dp(44),-1,Gravity.TOP|Gravity.RIGHT));
        flow=new Flow(context);
        setRows(1);setCandidates(List.of());
    }
    void setAnnotationVisibility(boolean characters,boolean ai){showBadges=characters;showAi=ai;populate(flow);}
    void setSelectedCandidate(int index){
        selectedCandidate=index;
        for(int i=0;i<flow.getChildCount();i++)flow.getChildAt(i).setSelected(i==index);
        if(index>=0&&index<flow.getChildCount()){
            View selected=flow.getChildAt(index);
            selected.post(()->{if(selected.isSelected())selected.requestRectangleOnScreen(new android.graphics.Rect(0,0,selected.getWidth(),selected.getHeight()),false);});
        }
    }
    void setKeyboardHeight(int height){if(keyboardHeight==height)return;keyboardHeight=height;requestLayout();}
    void setTypeface(Typeface value){if(typeface==value)return;typeface=value;populate(flow);requestLayout();}
    void setTextSp(int value){value=Math.max(12,Math.min(30,value));if(textSp==value)return;textSp=value;populate(flow);requestLayout();}
    int rowHeight(){return WidthLabelView.candidateLineHeight(textSp*getResources().getDisplayMetrics().scaledDensity)+badgeSpace()+dp(4);}
    private int badgeSpace(){return Math.max(dp(9),Math.round(11*getResources().getDisplayMetrics().scaledDensity));}
    void setExpanded(boolean value){
        if(expanded==value)return;
        boolean changed=expanded!=value;expanded=value;expandButton.setImageDrawable(UiIcons.drawable(getContext(),value?R.drawable.ic_collapse:R.drawable.ic_expand));
        String description=value?"変換候補を閉じる":"変換候補をすべて表示";expandButton.setContentDescription(description);expandButton.setTooltipText(description);
        if(changed)configureScroller();requestLayout();
    }
    void setRows(int value){
        if(rows==Math.max(1,Math.min(3,value))&&strip.getChildCount()>0)return;
        rows=Math.max(1,Math.min(3,value));configureScroller();requestLayout();
    }
    private void configureScroller(){
        if(flow.getParent() instanceof ViewGroup previous)previous.removeView(flow);strip.removeAllViews();flow.wrap=expanded||rows>1;
        // DPAD events leave touch mode; do not light up the whole candidate strip on focus.
        if(!flow.wrap){
            HorizontalScrollView scroll=new HorizontalScrollView(getContext());scroll.setDefaultFocusHighlightEnabled(false);scroll.setHorizontalScrollBarEnabled(false);scroll.addView(flow,new HorizontalScrollView.LayoutParams(-2,-1));strip.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        }else{
            ScrollView scroll=new ScrollView(getContext());scroll.setDefaultFocusHighlightEnabled(false);scroll.addView(flow,new ScrollView.LayoutParams(-1,-2));strip.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        }
    }
    void setCandidates(List<String> values){
        setCandidates(values,null);
    }
    void setCandidates(List<String> values,String ai){
        setCandidates(values,ai,true);
    }
    void setCandidates(List<String> values,String ai,boolean resetScroll){
        if(choices.equals(values)&&Objects.equals(aiCandidate,ai))return;
        if(choices.equals(values)){
            aiCandidate=ai;
            for(int index=0;index<flow.getChildCount();index++)((WidthLabelView)flow.getChildAt(index)).setAi(choices.get(index).equals(ai));
            return;
        }
        aiCandidate=ai;
        choices=new ArrayList<>(values);populate(flow);expandButton.setVisibility(choices.isEmpty()?INVISIBLE:VISIBLE);
        if(choices.isEmpty())setExpanded(false);
        if(resetScroll&&strip.getChildCount()>0)strip.getChildAt(0).scrollTo(0,0);
    }
    private void populate(Flow flow){
        Map<String,String> badges=CharacterWidth.badges(choices);
        // Keep the same text-to-padding ratio as 18sp text with 10dp padding
        // at the default system font scale.
        int horizontalPadding=Math.round(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,textSp,getResources().getDisplayMetrics())*10f/18f);
        while(flow.getChildCount()>choices.size())flow.removeViewAt(flow.getChildCount()-1);
        for(int index=0;index<choices.size();index++){
            WidthLabelView text;
            if(index<flow.getChildCount())text=(WidthLabelView)flow.getChildAt(index);
            else{
                text=new WidthLabelView(getContext());text.setTextColor(Color.rgb(235,242,246));
                text.setGravity(Gravity.CENTER);text.setSingleLine(true);text.useCandidateTypography();text.setEllipsize(TextUtils.TruncateAt.END);text.setMinWidth(dp(56));
                android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();
                background.addState(new int[]{android.R.attr.state_selected},new ColorDrawable(Color.rgb(69,104,118)));
                background.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));
                text.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.rgb(69,104,118)),background,new ColorDrawable(Color.WHITE)));
                text.setOnClickListener(view->select.accept(((WidthLabelView)view).valueForClick()));
                text.setOnLongClickListener(view->{Toast.makeText(getContext(),((TextView)view).getText(),Toast.LENGTH_SHORT).show();return true;});flow.addView(text);
            }
            String value=choices.get(index);text.setValue(value);text.setBadge(badges.get(value));text.setAi(value.equals(aiCandidate));text.setAnnotationVisibility(showBadges,showAi);text.setTypeface(typeface);text.setTextSize(textSp);text.setPadding(horizontalPadding,0,horizontalPadding,0);
            text.setSelected(index==selectedCandidate);
        }
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        // A scrolling single row keeps the button outside its viewport. Wrapped
        // candidates use the full width and leave room only in their first row.
        FrameLayout.LayoutParams stripParams=(FrameLayout.LayoutParams)strip.getLayoutParams();
        stripParams.rightMargin=!flow.wrap&&expandButton.getVisibility()==VISIBLE?dp(44):0;
        int controlHeight=rowHeight();
        for(ImageButton button:new ImageButton[]{expandButton}){
            button.getLayoutParams().height=controlHeight;
            int padding=Math.min(dp(12),Math.max(0,(controlHeight-dp(20))/2));button.setPadding(dp(12),padding,dp(12),padding);
        }
        super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(rows*rowHeight()+(expanded?keyboardHeight:0),MeasureSpec.EXACTLY));
    }

    private final class Flow extends ViewGroup {
        private boolean wrap;
        private final Paint divider=new Paint();
        private CandidateLayout.Result placement=new CandidateLayout.Result(List.of(),0,0);
        Flow(Context context){super(context);setWillNotDraw(false);divider.setColor(Color.rgb(61,76,88));}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int inset=expandButton.getVisibility()==VISIBLE?dp(44):0;
            int viewport=MeasureSpec.getMode(widthSpec)==MeasureSpec.UNSPECIFIED?Math.max(dp(48),CandidateBarView.this.getWidth()-(wrap?0:inset)):MeasureSpec.getSize(widthSpec);
            if(viewport<=0)viewport=Math.max(dp(48),getResources().getDisplayMetrics().widthPixels-(wrap?0:inset));
            int[] widths=new int[getChildCount()];
            for(int i=0;i<getChildCount();i++){
                View child=getChildAt(i);child.measure(MeasureSpec.makeMeasureSpec(viewport,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(rowHeight(),MeasureSpec.EXACTLY));widths[i]=child.getMeasuredWidth();
            }
            placement=CandidateLayout.pack(viewport,rowHeight(),widths,wrap,wrap?inset:0);
            for(var cell:placement.cells())getChildAt(cell.index()).measure(MeasureSpec.makeMeasureSpec(cell.width(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(rowHeight(),MeasureSpec.EXACTLY));
            setMeasuredDimension(resolveSize(placement.width(),widthSpec),resolveSize(placement.height(),heightSpec));
        }
        @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
            for(var cell:placement.cells())getChildAt(cell.index()).layout(cell.x(),cell.y(),cell.x()+cell.width(),cell.y()+rowHeight());
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            for(int index=0;index+1<placement.cells().size();index++){
                var cell=placement.cells().get(index);if(placement.cells().get(index+1).y()!=cell.y())continue;
                canvas.drawRect(cell.x()+cell.width()-dp(1),cell.y()+rowHeight()/2f-dp(10),cell.x()+cell.width(),cell.y()+rowHeight()/2f+dp(10),divider);
            }
        }
        @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(-2,-2);}
    }
}
