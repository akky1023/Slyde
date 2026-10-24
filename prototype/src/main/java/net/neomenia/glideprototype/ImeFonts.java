package net.neomenia.glideprototype;

import android.content.Context;
import android.graphics.*;
import android.icu.text.BreakIterator;
import android.text.*;
import android.text.style.MetricAffectingSpan;
import android.util.LruCache;
import java.io.*;
import java.util.*;

final class ImeFonts {
    static final String[] NAMES={"System","Noto Sans JP","Bizin Gothic Discord"};
    private static final String JP="NotoSansJP.ttf",BIZIN="BizinGothicDiscord-Regular.ttf";
    private static final String[] FALLBACK={JP,"NotoSansSymbols-Regular.ttf","NotoSansSymbols2-Regular.ttf"};
    private static final Map<String,Typeface> FONTS=new LinkedHashMap<>();
    private static final Map<String,BitSet> COVERAGE=new HashMap<>();
    private record ResolutionKey(Typeface selected,String text) {}
    private static final LruCache<ResolutionKey,Typeface> RESOLVED=new LruCache<>(512);
    static Typeface load(Context context,int selected){
        return selected==1?file(context,JP):selected==2?file(context,BIZIN):Typeface.DEFAULT;
    }
    private static Typeface file(Context context,String name){
        if(!FONTS.containsKey(name)){
            Typeface.Builder builder=new Typeface.Builder(context.getAssets(),"fonts/"+name).setWeight(400);
            if(name.equals(JP))builder.setFontVariationSettings("'wght' 400");
            Typeface font=builder.build();if(font==null)throw new IllegalStateException("フォントを読み込めません："+name);
            FONTS.put(name,font);
        }
        return FONTS.get(name);
    }
    private static void readCoverage(Context context){
        if(!COVERAGE.isEmpty())return;
        try(DataInputStream input=new DataInputStream(context.getAssets().open("fonts/coverage.bin"))){
            if(input.readInt()!=0x46434f56)throw new IOException("Invalid font coverage");
            int count=input.readInt();
            for(int i=0;i<count;i++){
                String name=input.readUTF();byte[] digest=new byte[32];input.readFully(digest);
                BitSet points=new BitSet();int ranges=input.readInt();
                for(int j=0;j<ranges;j++){int start=input.readInt(),end=input.readInt();points.set(start,end+1);}
                COVERAGE.put(name,points);
            }
        }catch(IOException error){throw new IllegalStateException("フォントの収録情報を読み込めません",error);}
    }
    private static boolean covers(BitSet points,String text){
        if(points==null)return false;
        for(int i=0;i<text.length();){
            int cp=text.codePointAt(i);i+=Character.charCount(cp);
            if(cp==0xfe0e||(cp>=0xe0100&&cp<=0xe01ef))continue;
            if(!points.get(cp))return false;
        }
        return true;
    }
    static String source(Typeface face){
        for(var entry:FONTS.entrySet())if(entry.getValue().equals(face))return entry.getKey();
        return "System";
    }
    /** Check the actual bundled cmap, before Android's implicit system fallback can win. */
    static Typeface forSymbol(Context context,Typeface selected,String text){
        if(selected==null)selected=Typeface.DEFAULT;
        String name=source(selected);
        ResolutionKey key=new ResolutionKey(selected,text);
        Typeface cached=RESOLVED.get(key);if(cached!=null)return cached;
        Typeface resolved;
        // Keep emoji presentation and joined sequences intact in the system emoji font.
        if(text.indexOf('\ufe0f')>=0||text.indexOf('\u200d')>=0)resolved=Typeface.DEFAULT;
        else{
            readCoverage(context);
            boolean supported;
            if(!name.equals("System"))supported=covers(COVERAGE.get(name),text);
            else{Paint probe=new Paint();probe.setTypeface(selected);supported=probe.hasGlyph(text);}
            resolved=supported?selected:null;
            if(resolved==null)for(String fallback:FALLBACK)if(covers(COVERAGE.get(fallback),text)){resolved=file(context,fallback);break;}
            if(resolved==null)resolved=Typeface.DEFAULT;
        }
        RESOLVED.put(key,resolved);return resolved;
    }
    /** Per-grapheme spans preserve mixed Japanese/symbol text on Android 8 and later. */
    static CharSequence styled(Context context,Typeface selected,String value){
        if(value.isEmpty())return value;
        BreakIterator boundaries=BreakIterator.getCharacterInstance(Locale.ROOT);boundaries.setText(value);
        SpannableString result=null;int start=boundaries.first(),runStart=0;Typeface run=null;
        for(int end=boundaries.next();end!=BreakIterator.DONE;start=end,end=boundaries.next()){
            // Old ICU versions do not know every modern emoji join sequence.
            while(end<value.length()&&continuesCluster(value,end))end=boundaries.next();
            Typeface face=forSymbol(context,selected,value.substring(start,end));
            if(run!=null&&!run.equals(face)){result=addSpan(value,result,selected,run,runStart,start);runStart=start;}
            run=face;
        }
        result=addSpan(value,result,selected,run,runStart,value.length());
        return result==null?value:result;
    }
    private static SpannableString addSpan(String value,SpannableString result,Typeface selected,Typeface face,int start,int end){
        if(face.equals(selected==null?Typeface.DEFAULT:selected))return result;
        if(result==null)result=new SpannableString(value);
        result.setSpan(new FallbackSpan(face),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return result;
    }
    private static boolean continuesCluster(String value,int boundary){
        int next=value.codePointAt(boundary),previous=value.codePointBefore(boundary);
        int type=Character.getType(next);
        if(next==0x200d||previous==0x200d||next==0xfe0e||next==0xfe0f||
            (next>=0x1f3fb&&next<=0x1f3ff)||(next>=0xe0020&&next<=0xe007f)||(next>=0xe0100&&next<=0xe01ef)||
            type==Character.NON_SPACING_MARK||type==Character.COMBINING_SPACING_MARK||type==Character.ENCLOSING_MARK)return true;
        if(next>=0x1f1e6&&next<=0x1f1ff){
            int count=0;for(int i=boundary;i>0;){int cp=value.codePointBefore(i);if(cp<0x1f1e6||cp>0x1f1ff)break;count++;i-=Character.charCount(cp);}
            return count%2==1;
        }
        return false;
    }
    static final class FallbackSpan extends MetricAffectingSpan {
        final Typeface typeface;
        FallbackSpan(Typeface value){typeface=value;}
        @Override public void updateDrawState(TextPaint paint){paint.setTypeface(typeface);}
        @Override public void updateMeasureState(TextPaint paint){paint.setTypeface(typeface);}
    }
    static void apply(android.widget.TextView view,Typeface face){
        view.setTypeface(face);
        if(!(view instanceof WidthLabelView))view.setText(styled(view.getContext(),face,view.getText().toString()));
    }
    static void drawText(Context context,Canvas canvas,String value,float x,float baseline,Paint paint){
        CharSequence text=styled(context,paint.getTypeface(),value);
        if(!(text instanceof Spanned)){canvas.drawText(value,x,baseline,paint);return;}
        TextPaint styledPaint=new TextPaint(paint);styledPaint.setTextAlign(Paint.Align.LEFT);
        int width=Math.max(1,(int)Math.ceil(Layout.getDesiredWidth(text,styledPaint)));
        StaticLayout layout=StaticLayout.Builder.obtain(text,0,text.length(),styledPaint,width).setIncludePad(false).build();
        float left=paint.getTextAlign()==Paint.Align.CENTER?x-width/2f:paint.getTextAlign()==Paint.Align.RIGHT?x-width:x;
        int saved=canvas.save();canvas.translate(left,baseline-layout.getLineBaseline(0));layout.draw(canvas);canvas.restoreToCount(saved);
    }
}
