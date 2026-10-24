package net.neomenia.glideprototype;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.text.Layout;
import android.widget.TextView;
import android.view.MotionEvent;
import net.neomenia.inputcore.CharacterWidth;

/** Width annotation is drawn separately and never becomes committed text. */
@android.annotation.SuppressLint("AppCompatCustomView") // IME retains its platform theme; only settings use AppCompat.
final class WidthLabelView extends TextView {
    private final Paint badgePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String badge="";
    private boolean ai=false;
    private boolean showBadge=true,showAi=true;
    private final Paint.FontMetrics badgeMetrics=new Paint.FontMetrics();
    private final Rect candidateBounds=new Rect();
    private boolean candidateTypography=false;
    private boolean symbolTypography=false;
    private boolean emojiTypography;
    private boolean fontReady;
    private String touchedValue,clickedValue;
    WidthLabelView(Context context){super(context);badgePaint.setTextAlign(Paint.Align.RIGHT);badgePaint.setColor(Color.rgb(160,191,207));badgePaint.setTextSize(9*getResources().getDisplayMetrics().scaledDensity);setPadding(0,0,0,Math.round(8*getResources().getDisplayMetrics().density));fontReady=true;}
    void setValue(String value){if(value.contentEquals(getText()))return;setText(ImeFonts.styled(getContext(),getTypeface(),value));setBadge(CharacterWidth.badge(value));}
    @Override public void setTypeface(Typeface value){if(fontReady&&getTypeface()==value)return;super.setTypeface(value);if(fontReady)setText(ImeFonts.styled(getContext(),value,getText().toString()));}
    void setBadge(String value){badge=value;updateDescription();invalidate();}
    void setAi(boolean value){ai=value;updateDescription();invalidate();}
    void setAnnotationVisibility(boolean characters,boolean aiLabel){showBadge=characters;showAi=aiLabel;updateDescription();invalidate();}
    private void updateDescription(){setContentDescription(getText()+(showBadge&&!badge.isEmpty()?" "+badge:"")+(showAi&&ai?" AI候補":""));}
    // A shared em-based line box avoids the unusually large global metrics of
    // bundled fonts. Center their Japanese glyphs, rather than those metrics.
    static int candidateLineHeight(float textSize){return (int)Math.ceil(textSize*1.3f);}
    void useCandidateTypography(){candidateTypography=true;setIncludeFontPadding(false);setHorizontallyScrolling(false);}
    void useSymbolTypography(){symbolTypography=true;setSingleLine(true);setIncludeFontPadding(false);}
    void setEmojiTypography(boolean value){emojiTypography=value;invalidate();}
    // TextView otherwise scrolls its oversized font line box into view during layout.
    @Override public void scrollTo(int x,int y){if(symbolTypography)super.scrollTo(0,0);else super.scrollTo(x,y);}
    private void drawSymbol(Canvas canvas){
        String value=getText().toString();if(value.isEmpty())return;
        Paint paint=new Paint(getPaint());paint.setTypeface(emojiTypography?Typeface.DEFAULT:ImeFonts.forSymbol(getContext(),getTypeface(),value));paint.setColor(getCurrentTextColor());paint.getTextBounds(value,0,value.length(),candidateBounds);
        if(candidateBounds.isEmpty())return;
        float d=getResources().getDisplayMetrics().density;
        float width=Math.max(1,getWidth()-6*d),height=Math.max(1,getHeight()-6*d);
        float scale=Math.min(1,Math.min(width/candidateBounds.width(),height/candidateBounds.height()));
        int saved=canvas.save();
        canvas.translate(getWidth()/2f,getHeight()/2f);canvas.scale(scale,scale);
        canvas.drawText(value,-(candidateBounds.left+candidateBounds.right)/2f,-(candidateBounds.top+candidateBounds.bottom)/2f,paint);
        canvas.restoreToCount(saved);
    }
    private float candidateBaseline(){
        getPaint().getTextBounds("国",0,1,candidateBounds);
        return getHeight()/2f-(candidateBounds.top+candidateBounds.bottom)/2f;
    }
    @Override public int getBaseline(){return candidateTypography?Math.round(candidateBaseline()):super.getBaseline();}
    String valueForClick(){return clickedValue!=null?clickedValue:getText().toString();}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN)touchedValue=getText().toString();
        else if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)touchedValue=null;
        return super.onTouchEvent(event);
    }
    @Override public boolean performClick(){
        clickedValue=touchedValue;touchedValue=null;try{return super.performClick();}finally{clickedValue=null;}
    }
    @Override protected void onDraw(Canvas canvas){
        if(symbolTypography){drawSymbol(canvas);}
        else if(candidateTypography){
            Layout layout=getLayout();
            if(layout!=null){
                getPaint().setColor(getCurrentTextColor());
                int saved=canvas.save();
                canvas.clipRect(getPaddingLeft(),getPaddingTop(),getWidth()-getPaddingRight(),getHeight()-getPaddingBottom());
                canvas.translate(getPaddingLeft(),candidateBaseline()-layout.getLineBaseline(0));
                layout.draw(canvas);canvas.restoreToCount(saved);
            }
        }else super.onDraw(canvas);
        // Keep annotation metrics independent of the selected candidate font.
        badgePaint.setTypeface(Typeface.DEFAULT);badgePaint.getFontMetrics(badgeMetrics);
        float d=getResources().getDisplayMetrics().density;
        float annotationHeight=Math.max(getPaddingBottom(),badgeMetrics.descent-badgeMetrics.ascent);
        float baseline=getHeight()-annotationHeight/2f-(badgeMetrics.ascent+badgeMetrics.descent)/2f;
        if(showBadge){badgePaint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(badge,getWidth()-3*d,baseline,badgePaint);}
        if(showAi&&ai){badgePaint.setTextAlign(Paint.Align.LEFT);canvas.drawText("AI",3*d,baseline,badgePaint);}
    }
}
