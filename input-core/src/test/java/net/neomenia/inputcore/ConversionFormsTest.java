package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConversionFormsTest {
    @Test public void halfWidthKanaIncludesVoicingSmallKanaAndLongVowels(){
        assertEquals("ｶﾞｯﾂﾎﾟｰｽﾞ",ConversionForms.halfWidthKatakana("がっつぽーず"));
        assertEquals("ｳﾞｨｵﾘﾝ",ConversionForms.halfWidthKatakana("ヴぃおりん"));
        assertEquals("｢ﾃｽﾄ､｡｣",ConversionForms.halfWidthKatakana("「てすと、。」"));
        assertEquals("漢字ABC😀ｶﾞ",ConversionForms.halfWidthKatakana("漢字ABC😀が"));
        assertEquals("ｶﾞ",ConversionForms.halfWidthKatakana("ｶﾞ"));
    }
    @Test public void numericWidthsPreserveSignsAndDoNotAlterEmoji(){
        assertEquals("０１２３４５６７８９",ConversionForms.fullWidthNumber("0123456789"));
        assertEquals("-１２.３%",ConversionForms.fullWidthNumber("-12.3%"));
        assertEquals("１２３",ConversionForms.fullWidthNumber("１２3"));
        for(String value:new String[]{"1️⃣","①","漢字","123abc","😀"})assertEquals(value,ConversionForms.fullWidthNumber(value));
    }
}
