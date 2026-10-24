package net.neomenia.glideprototype;

import android.content.Context;
import android.graphics.*;
import android.text.Spanned;
import java.io.*;
import java.security.MessageDigest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,28,35})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImeFontsTest {
    private Context context(){return RuntimeEnvironment.getApplication();}
    private String source(Typeface face,String text){return ImeFonts.source(ImeFonts.forSymbol(context(),face,text));}
    @Test public void keepsSelectedGlyphsThenUsesJapaneseSymbolsAndSystemInOrder(){
        Typeface bizin=ImeFonts.load(context(),2),jp=ImeFonts.load(context(),1);
        assertSame(bizin,ImeFonts.forSymbol(context(),bizin,"あ"));
        assertSame(bizin,ImeFonts.forSymbol(context(),bizin,"①"));
        assertSame(bizin,ImeFonts.forSymbol(context(),bizin,"✔"));
        assertSame(jp,ImeFonts.forSymbol(context(),bizin,"㊀"));
        assertEquals("NotoSansSymbols-Regular.ttf",source(bizin,"↊"));
        assertEquals("NotoSansSymbols2-Regular.ttf",source(bizin,"⏻"));
        assertEquals("NotoSansSymbols2-Regular.ttf",source(jp,"✔"));
        assertEquals("NotoSansSymbols2-Regular.ttf",source(jp,"⠁"));
        assertSame(Typeface.DEFAULT,ImeFonts.forSymbol(context(),bizin,"⨀"));
        assertSame(Typeface.DEFAULT,ImeFonts.forSymbol(context(),Typeface.DEFAULT,"あ"));
        assertSame(Typeface.DEFAULT_BOLD,ImeFonts.forSymbol(context(),Typeface.DEFAULT_BOLD,"あ"));
        for(String symbol:new String[]{"↊","⏻","⠁"}){
            Paint paint=new Paint();paint.setTypeface(ImeFonts.forSymbol(context(),bizin,symbol));assertTrue(symbol,paint.hasGlyph(symbol));
        }
    }
    @Test public void mixedTextUsesPerCharacterFontsAndRetainsTheCommittedValue(){
        Typeface bizin=ImeFonts.load(context(),2);String value="あ㊀↊⏻⨀";
        CharSequence styled=ImeFonts.styled(context(),bizin,value);assertEquals(value,styled.toString());assertTrue(styled instanceof Spanned);
        Spanned spans=(Spanned)styled;String[] expected={"BizinGothicDiscord-Regular.ttf","NotoSansJP.ttf","NotoSansSymbols-Regular.ttf","NotoSansSymbols2-Regular.ttf","System"};
        for(int i=0;i<value.length();i++){
            ImeFonts.FallbackSpan[] run=spans.getSpans(i,i+1,ImeFonts.FallbackSpan.class);
            assertEquals(expected[i],ImeFonts.source(run.length==0?bizin:run[0].typeface));
        }
        WidthLabelView cell=new WidthLabelView(context());cell.setTypeface(bizin);cell.setValue(value);
        assertEquals(value,cell.valueForClick());assertEquals(value,cell.getText().toString());
        cell.setTypeface(ImeFonts.load(context(),1));assertEquals(value,cell.valueForClick());
    }
    @Test public void emojiSequencesStayWholeWhenFontsChange(){
        Typeface bizin=ImeFonts.load(context(),2);
        for(String emoji:new String[]{"👨‍👩‍👧‍👦","🏳️‍🌈","🇯🇵","👍🏽","❤️","1️⃣"}){
            String value="あ"+emoji+"い";Spanned styled=(Spanned)ImeFonts.styled(context(),bizin,value);
            assertEquals(value,styled.toString());ImeFonts.FallbackSpan[] spans=styled.getSpans(1,1+emoji.length(),ImeFonts.FallbackSpan.class);
            assertEquals(emoji,1,spans.length);assertEquals(1,styled.getSpanStart(spans[0]));assertEquals(1+emoji.length(),styled.getSpanEnd(spans[0]));
            assertSame(Typeface.DEFAULT,spans[0].typeface);
        }
    }
    @Test public void coverageMetadataMatchesEveryBundledFont() throws Exception {
        try(DataInputStream input=new DataInputStream(context().getAssets().open("fonts/coverage.bin"))){
            assertEquals(0x46434f56,input.readInt());int count=input.readInt();assertEquals(4,count);
            for(int i=0;i<count;i++){
                String name=input.readUTF();byte[] expected=new byte[32];input.readFully(expected);MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(InputStream font=context().getAssets().open("fonts/"+name)){byte[] bytes=new byte[8192];int n;while((n=font.read(bytes))!=-1)digest.update(bytes,0,n);}
                assertArrayEquals(name,expected,digest.digest());int ranges=input.readInt();int previous=-1;
                for(int j=0;j<ranges;j++){int start=input.readInt(),end=input.readInt();assertTrue(start>previous&&end>=start);previous=end;}
            }
            assertEquals(-1,input.read());
        }
    }
}
