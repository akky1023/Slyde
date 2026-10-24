package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;

public class JapaneseCompositionTest {
    @Test public void retainsActualSpellingAcrossTapsAndGlides(){
        JapaneseComposition text=new JapaneseComposition();
        for(String raw:new String[]{"te","su","to","shi","qi"})text.append(raw);
        assertEquals("てすとしくぃ",text.toString());assertEquals("tesutoshiqi",text.alphabet());
    }
    @Test public void confirmedDoubleNDoesNotRecombineWithNextVowel(){
        JapaneseComposition text=new JapaneseComposition();
        text.append("n");text.append("n");text.append("a");
        assertEquals("んあ",text.toString());assertEquals("nna",text.alphabet());
        text.delete();assertEquals("ん",text.toString());assertEquals("nn",text.alphabet());
        text.append("ya");assertEquals("んや",text.toString());
    }
    @Test public void internalNApostropheIsOmittedFromAlphabet(){
        JapaneseComposition text=new JapaneseComposition();text.append("kun'");
        assertEquals("くん",text.toString());assertEquals("kun",text.alphabet());
    }
    @Test public void deletePreservesPendingConsonantsAndEarlierOriginalSpellings(){
        JapaneseComposition text=new JapaneseComposition();text.append("shi");text.append("k");text.append("a");
        text.delete();assertEquals("し",text.toString());assertEquals("shi",text.alphabet());
        text.append("k");text.delete();assertEquals("shi",text.alphabet());
    }
    @Test public void deletingPartOfDigraphRetainsCorrectRemainingKana(){
        JapaneseComposition text=new JapaneseComposition();text.append("shi");text.append("kya");
        text.delete();assertEquals("しき",text.toString());assertEquals("shiki",text.alphabet());
        text.delete();assertEquals("し",text.toString());assertEquals("shi",text.alphabet());
    }
    @Test public void deletingPartOfGlideUsesItsOriginalPrefixWhenPossible(){
        JapaneseComposition text=new JapaneseComposition();text.append("tesuto");text.delete();
        assertEquals("てす",text.toString());assertEquals("tesu",text.alphabet());
        text.clear();text.append("a");assertEquals("a",text.alphabet());
    }
}
