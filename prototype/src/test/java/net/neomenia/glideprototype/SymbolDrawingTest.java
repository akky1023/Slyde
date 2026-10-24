package net.neomenia.glideprototype;

import android.graphics.*;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SymbolDrawingTest {
    private WidthLabelView cell(int font,String value,int textSize,boolean badges){
        WidthLabelView cell=new WidthLabelView(RuntimeEnvironment.getApplication());cell.useSymbolTypography();
        cell.setTypeface(ImeFonts.load(cell.getContext(),font));cell.setTextSize(textSize);cell.setTextColor(Color.WHITE);
        cell.setValue(value);cell.setAnnotationVisibility(badges,false);
        cell.measure(View.MeasureSpec.makeMeasureSpec(60,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(48,View.MeasureSpec.EXACTLY));cell.layout(0,0,60,48);return cell;
    }
    private long ink(Bitmap bitmap){
        long total=0;for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)total+=Color.alpha(bitmap.getPixel(x,y));return total;
    }
    @Test public void bundledFontsKeepTheEntireSymbolRatherThanClippingItsLineBox(){
        for(int font=0;font<3;font++)for(String value:new String[]{"（","）","㋿","㍻","㊿","∫","∑","→","⌘","█","㊀","↊","⏻","⠁"}){
            WidthLabelView cell=cell(font,value,25,false);
            Bitmap actual=Bitmap.createBitmap(60,48,Bitmap.Config.ARGB_8888),reference=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888);
            try{
                cell.draw(new Canvas(actual));Paint paint=new Paint(cell.getPaint());paint.setColor(cell.getCurrentTextColor());paint.setTypeface(ImeFonts.forSymbol(cell.getContext(),cell.getTypeface(),value));Rect bounds=new Rect();paint.getTextBounds(value,0,value.length(),bounds);
                assertTrue("font="+font+" value="+value+" bounds="+bounds,bounds.width()<=54&&bounds.height()<=42);
                new Canvas(reference).drawText(value,100-(bounds.left+bounds.right)/2f,100-(bounds.top+bounds.bottom)/2f,paint);
                long expected=ink(reference);assertTrue(value,expected>0);
                assertEquals("font="+font+" value="+value,expected,ink(actual),expected*.02);
            }finally{actual.recycle();reference.recycle();}
        }
    }
    @Test public void oversizedSymbolsFitInsideTheCell(){
        for(int font=0;font<3;font++)for(String value:new String[]{"㋿","㍻","（","█","👨‍👩‍👧‍👦"}){
            WidthLabelView cell=cell(font,value,90,true);Bitmap bitmap=Bitmap.createBitmap(60,48,Bitmap.Config.ARGB_8888);
            try{
                cell.draw(new Canvas(bitmap));assertTrue(value,ink(bitmap)>0);
                // The glyph must retain an inset on every edge. Ignore the independently drawn badge.
                for(int y=0;y<36;y++)for(int x=0;x<60;x++)if(x<2||x>=58||y<2)assertEquals("font="+font+" value="+value,0,Color.alpha(bitmap.getPixel(x,y)));
            }finally{bitmap.recycle();}
        }
    }
}
