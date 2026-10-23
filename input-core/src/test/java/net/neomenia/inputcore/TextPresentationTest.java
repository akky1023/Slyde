package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;

public class TextPresentationTest {
    @Test public void onlyConfusableCharactersReceiveBadges(){
        for(String value:new String[]{"、","。","（","）","，","．","Ａ","ア","　"})assertEquals(value,"[全]",CharacterWidth.badge(value));
        for(String value:new String[]{",",".","(",")","A","ｱ"," "})assertEquals(value,"[半]",CharacterWidth.badge(value));
        for(String value:new String[]{"漢字","漢字ABC","©","→","日本語","カタカナ","hello","😀","❤️","👨‍👩‍👧‍👦","🇯🇵","1️⃣",""})assertEquals(value,"",CharacterWidth.badge(value));
        for(String value:new String[]{"二","口","工","力","夕","卜","八","千","一","〇"})assertEquals(value,"[漢]",CharacterWidth.badge(value));
        for(String value:new String[]{"ニ","ロ","エ","カ","タ","ト","ハ","チ","ヘ","ベ","ペ","ー"})assertEquals(value,"[カナ]",CharacterWidth.badge(value));
        for(String value:new String[]{"へ","べ","ぺ"})assertEquals(value,"[かな]",CharacterWidth.badge(value));
    }
    @Test public void widthVariantsAreAnnotatedTogetherWithoutCompatibilityFalsePositives(){
        var labels=CharacterWidth.badges(java.util.List.of("ABC","ＡＢＣ","カタカナ","ｶﾀｶﾅ","ガ","ｶﾞ","二","ニ","①","1","漢字"));
        assertEquals("[半]",labels.get("ABC"));assertEquals("[全]",labels.get("ＡＢＣ"));
        assertEquals("[全]",labels.get("カタカナ"));assertEquals("[半]",labels.get("ｶﾀｶﾅ"));
        assertEquals("[全]",labels.get("ガ"));assertEquals("[半]",labels.get("ｶﾞ"));
        assertEquals("[漢]",labels.get("二"));assertEquals("[カナ]",labels.get("ニ"));
        assertEquals("",labels.get("①"));assertEquals("",labels.get("漢字"));
    }
    @Test public void punctuationOptionsAreIndependentAndDoNotChangeAscii(){
        String input="あ、い。（う） , . ( )";
        for(int flags=0;flags<16;flags++){
            JapaneseTypography style=new JapaneseTypography((flags&1)!=0,(flags&2)!=0,(flags&4)!=0,(flags&8)!=0);
            String expected="あ"+((flags&1)!=0?"，":"、")+"い"+((flags&2)!=0?"．":"。")+((flags&4)!=0?"(":"（")+"う"+((flags&8)!=0?")":"）")+" , . ( )";
            assertEquals(expected,style.format(input));assertEquals(expected,style.format(style.format(input)));
        }
    }
    @Test public void shiftIsOneShotAndCapsLockPersistsUntilTapped(){
        ShiftState shift=new ShiftState();assertFalse(shift.upper());shift.tap();assertTrue(shift.upper());assertFalse(shift.locked());
        shift.typed("ka");assertFalse(shift.upper());
        shift.tap();shift.tap();assertTrue(shift.locked());shift.typed("test");assertTrue(shift.upper());
        shift.tap();assertFalse(shift.upper());assertFalse(shift.locked());
    }
    @Test public void shiftCyclesAndTypingRestartsAtOneShot(){
        ShiftState shift=new ShiftState();
        for(int cycle=0;cycle<3;cycle++){
            shift.tap();assertTrue(shift.upper());assertFalse(shift.locked());
            shift.tap();assertTrue(shift.upper());assertTrue(shift.locked());
            shift.tap();assertFalse(shift.upper());assertFalse(shift.locked());
        }
        shift.tap();shift.typed("a");shift.tap();assertTrue(shift.upper());assertFalse(shift.locked());
        shift.typed("'");assertTrue(shift.upper());shift.typed("a");assertFalse(shift.upper());
    }
}
