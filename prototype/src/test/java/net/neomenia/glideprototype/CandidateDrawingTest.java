package net.neomenia.glideprototype;

import android.graphics.*;
import android.view.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CandidateDrawingTest {
    @Test public void conversionIncludesFullWidthNumbersAndHalfWidthKana(){
        var style=new net.neomenia.inputcore.JapaneseTypography(false,false,false,false);
        assertEquals(List.of("123","１２３"),KeyboardSession.formatChoices(List.of("123"),"123",style));
        assertEquals(List.of("123","１２３","いちにさん","イチニサン","ｲﾁﾆｻﾝ"),KeyboardSession.formatChoices(List.of("123"),"いちにさん",style));
        assertEquals(List.of("頑張る","がんばる","ガンバル","ｶﾞﾝﾊﾞﾙ"),KeyboardSession.formatChoices(List.of("頑張る"),"がんばる",style));
        assertEquals(List.of("がっつぽーず","ガッツポーズ","ｶﾞｯﾂﾎﾟｰｽﾞ"),KeyboardSession.formatChoices(List.of(),"がっつぽーず",style));
        assertEquals(List.of("1️⃣"),KeyboardSession.formatChoices(List.of("1️⃣"),"1️⃣",style));
    }
    @Test public void wrappedRowsUseFullWidthExceptBesideTheExpandButton(){
        android.content.Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),android.R.style.Theme_Material_Light_NoActionBar);
        for(boolean expanded:new boolean[]{false,true}){
            CandidateBarView bar=new CandidateBarView(context,value->{},value->{});
            bar.setRows(3);bar.setKeyboardHeight(240);bar.setExpanded(expanded);
            bar.setCandidates(List.of("あ","い","う","え","お","か","き","く","け","こ","さ","し"));layoutBar(bar);
            ViewGroup cells=flow(bar);
            View button=bar.getChildAt(1);
            assertEquals(bar.getWidth(),cells.getWidth());
            assertEquals(bar.rowHeight(),button.getHeight());
            int rowEnds=0;
            for(int i=0;i<cells.getChildCount();i++){
                View cell=cells.getChildAt(i);
                if(i+1<cells.getChildCount()&&cells.getChildAt(i+1).getTop()==cell.getTop())continue;
                assertEquals(cell.getTop()==0?button.getLeft():bar.getWidth(),cell.getRight());rowEnds++;
            }
            assertTrue(rowEnds>=3);
            button.performClick();
        }
    }
    @Test public void flickBracketsKeepBothWidthsUnderEveryTypographySetting() throws Exception {
        var dictionary=new net.neomenia.inputcore.ConversionCandidates();
        for(boolean open:new boolean[]{false,true})for(boolean close:new boolean[]{false,true}){
            var style=new net.neomenia.inputcore.JapaneseTypography(false,false,open,close);
            for(String raw:List.of(net.neomenia.inputcore.KeyLayout.variant("e",3),net.neomenia.inputcore.KeyLayout.variant("o",3),"（）")){
                String input=style.format(raw);
                List<String> found=dictionary.convert(input,(part,count)->List.of(part),()->false);
                List<String> displayed=KeyboardSession.formatChoices(found,input,style);
                for(String value:raw.equals("（")?List.of("(","（","[","［","{","｛"):raw.equals("）")?List.of(")","）","]","］","}","｝"):List.of("()","（）","[]","［］","{}","｛｝"))assertTrue(input+" -> "+value,displayed.contains(value));
            }
        }
    }
    @Test public void aiAnnotationDoesNotResetTextLayoutOrScroll(){
        android.content.Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),android.R.style.Theme_Material_Light_NoActionBar);
        CandidateBarView bar=new CandidateBarView(context,value->{},value->{});
        List<String> values=List.of("今日の予定","明日の予定","来週の予定","来月の予定");
        bar.setCandidates(values);layoutBar(bar);
        ViewGroup cells=flow(bar);android.widget.HorizontalScrollView scroll=(android.widget.HorizontalScrollView)cells.getParent();
        scroll.scrollTo(60,0);int scrollX=scroll.getScrollX();assertTrue(scrollX>0);
        android.widget.TextView first=(android.widget.TextView)cells.getChildAt(0);
        android.text.Layout original=first.getLayout();CharSequence text=first.getText();
        bar.setCandidates(values,"来週の予定",false);layoutBar(bar);
        assertSame(text,first.getText());assertSame(original,first.getLayout());assertEquals(scrollX,scroll.getScrollX());
        assertTrue(cells.getChildAt(2).getContentDescription().toString().contains("AI候補"));
    }
    @Test public void newAiCandidatePreservesScrollAndUnchangedText(){
        android.content.Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),android.R.style.Theme_Material_Light_NoActionBar);
        CandidateBarView bar=new CandidateBarView(context,value->{},value->{});
        bar.setCandidates(List.of("今日の予定","明日の予定","来週の予定","来月の予定"));layoutBar(bar);
        ViewGroup cells=flow(bar);android.widget.HorizontalScrollView scroll=(android.widget.HorizontalScrollView)cells.getParent();
        scroll.scrollTo(60,0);int scrollX=scroll.getScrollX();assertTrue(scrollX>0);
        android.widget.TextView first=(android.widget.TextView)cells.getChildAt(0);CharSequence text=first.getText();
        bar.setCandidates(List.of("今日の予定","今日の予報","明日の予定","来週の予定","来月の予定"),"今日の予報",false);layoutBar(bar);
        assertSame(text,first.getText());assertEquals(scrollX,scroll.getScrollX());
        bar.setCandidates(List.of("次の読み","次の候補","別の候補","さらに候補"),null,true);layoutBar(bar);
        assertEquals(0,scroll.getScrollX());
    }
    private static void layoutBar(CandidateBarView bar){
        bar.measure(View.MeasureSpec.makeMeasureSpec(220,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.AT_MOST));
        bar.layout(0,0,220,bar.getMeasuredHeight());
    }
    @Test public void convertedSymbolsCommitPlainCharactersAndCompleteEmojiSequences() throws Exception {
        android.content.Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),android.R.style.Theme_Material_Light_NoActionBar);
        var dictionary=new net.neomenia.inputcore.ConversionCandidates();
        for(String[] sample:new String[][]{{"いげた","#"},{"いげた","＃"},{"なんばー","#"},{"なんばー","＃"},{"しゃーぷ","#"},{"はーと","♥"},{"にほんこっき","🇯🇵"},{"まるにじゅういち","㉑"},{"れいわ","㋿"}}){
            String[] selected={null};CandidateBarView bar=new CandidateBarView(context,value->selected[0]=value,value->{});
            // The IME lays out its empty candidate strip before the async dictionary result arrives.
            bar.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.AT_MOST));bar.layout(0,0,360,bar.getMeasuredHeight());
            List<String> candidates=dictionary.convert(sample[0],(reading,count)->List.of(reading),()->false);
            bar.setTypeface(ImeFonts.load(context,1));bar.setCandidates(candidates);
            bar.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.AT_MOST));bar.layout(0,0,360,bar.getMeasuredHeight());
            ViewGroup cells=flow(bar);assertNotNull(cells);int index=candidates.indexOf(sample[1]);assertTrue(sample[0],index>=0);
            android.text.Layout text=((android.widget.TextView)cells.getChildAt(index)).getLayout();assertNotNull(text);assertEquals(sample[0],0,text.getEllipsisCount(0));
            cells.getChildAt(index).performClick();assertEquals(sample[1],selected[0]);
            SettingsActivityTest.capture(bar,"conversion-symbol-"+sample[1].codePointAt(0),360,bar.getMeasuredHeight());
        }
    }
    @Test public void expandedListKeepsAll256CandidatesSelectable(){
        android.content.Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),android.R.style.Theme_Material_Light_NoActionBar);
        java.util.ArrayList<String> values=new java.util.ArrayList<>();for(int i=0;i<256;i++)values.add("記号"+i);
        String[] selected={null};
        CandidateBarView bar=new CandidateBarView(context,value->selected[0]=value,value->{});
        bar.setCandidates(values);bar.setKeyboardHeight(240);bar.setExpanded(true);
        bar.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.AT_MOST));bar.layout(0,0,360,bar.getMeasuredHeight());
        ViewGroup cells=flow(bar);assertNotNull(cells);assertEquals(256,cells.getChildCount());
        assertTrue(cells.getHeight()>bar.getHeight());
        cells.getChildAt(255).performClick();assertEquals("記号255",selected[0]);
    }
    private static ViewGroup flow(View view){
        if(view instanceof ViewGroup group){
            if(group.getChildCount()>0&&group.getChildAt(0) instanceof WidthLabelView)return group;
            for(int i=0;i<group.getChildCount();i++){ViewGroup found=flow(group.getChildAt(i));if(found!=null)return found;}
        }return null;
    }
    @Test public void onlyInternalCandidateBoundariesAreDrawnInCompactAndWrappedRows(){
        android.content.Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),android.R.style.Theme_Material_Light_NoActionBar);
        for(int rows:new int[]{1,3}){
            CandidateBarView bar=new CandidateBarView(context,value->{},value->{});bar.setRows(rows);bar.setCandidates(List.of("あ","い","う","え"));
            bar.measure(View.MeasureSpec.makeMeasureSpec(220,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.AT_MOST));bar.layout(0,0,220,bar.getMeasuredHeight());
            ViewGroup cells=flow(bar);assertNotNull(cells);Bitmap bitmap=Bitmap.createBitmap(cells.getWidth(),cells.getHeight(),Bitmap.Config.ARGB_8888);
            try{
                cells.draw(new Canvas(bitmap));int internal=0;
                for(int i=0;i<cells.getChildCount();i++){
                    View cell=cells.getChildAt(i);int pixel=bitmap.getPixel(cell.getRight()-1,cell.getTop()+cell.getHeight()/2);
                    boolean last=i+1==cells.getChildCount()||cells.getChildAt(i+1).getTop()!=cell.getTop();
                    if(last)assertNotEquals("row ends must not have dividers",Color.rgb(61,76,88),pixel);
                    else{assertEquals("internal dividers remain",Color.rgb(61,76,88),pixel);internal++;}
                }
                assertTrue(internal>0);
            }finally{bitmap.recycle();}
        }
    }
}
