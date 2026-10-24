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
    @Test public void convertedSymbolsCommitTheirCompletePresentationAndEmojiSequences() throws Exception {
        android.content.Context context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),android.R.style.Theme_Material_Light_NoActionBar);
        var dictionary=new net.neomenia.inputcore.ConversionCandidates();
        for(String[] sample:new String[][]{{"はーと","♥\ufe0e"},{"にほんこっき","🇯🇵"},{"まるにじゅういち","㉑"},{"れいわ","㋿"}}){
            String[] selected={null};CandidateBarView bar=new CandidateBarView(context,value->selected[0]=value,value->{},()->{});
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
        CandidateBarView bar=new CandidateBarView(context,value->selected[0]=value,value->{},()->{});
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
            CandidateBarView bar=new CandidateBarView(context,value->{},value->{},()->{});bar.setRows(rows);bar.setCandidates(List.of("あ","い","う","え"));
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
