package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;

public class RomanizerStreamingTest {
    private String input(String... taps){String state="";for(String tap:taps)state=Romanizer.append(state,tap);return state;}
    @Test public void nnThenVowelDoesNotReuseTheSecondN(){
        for(int i=0;i<5;i++)assertEquals("ん"+"あいうえお".charAt(i),input("n","n","aiueo".substring(i,i+1)));
        assertEquals("ん",input("n","n"));
    }
    @Test public void nextNCanStartANewSyllable(){assertEquals("んな",input("n","n","n","a"));assertEquals("んや",input("n","n","y","a"));}
    @Test public void singlePendingNStillMakesNRowAndPalatalizedKana(){assertEquals("な",input("n","a"));assertEquals("にゃ",input("n","y","a"));assertEquals("n",input("n"));}
    @Test public void batchRomajiAndGlideSyllablesKeepTheirMeaning(){
        assertEquals("こんにちは",Romanizer.convert("konnichiha",false));assertEquals("こんにちは",input("ko","n","ni","chi","ha"));
        assertEquals("てんき",input("ten'","ki"));assertEquals("がっこう",input("ga","ltu","kou"));
    }
    @Test public void wuSupportsStreamingAndBatchInput(){
        assertTrue(Romanizer.isSyllable("wu"));
        assertEquals("う",input("w","u"));
        assertEquals("う",Romanizer.convert("wu",false));
        assertEquals("う",Romanizer.convert("WU",true));
    }
    @Test public void whSyllablesSupportStreamingAndBatchInput(){
        String[] kana={"うぁ","うぃ","う","うぇ","うぉ"};
        assertEquals("wh",input("w","h"));
        assertTrue(Romanizer.isSyllablePrefix("wh"));
        for(int i=0;i<5;i++){
            String vowel="aiueo".substring(i,i+1),syllable="wh"+vowel;
            assertTrue(Romanizer.isSyllable(syllable));
            assertEquals(kana[i],input("w","h",vowel));
            assertEquals(kana[i],Romanizer.convert(syllable,false));
            assertEquals(kana[i],Romanizer.convert(syllable.toUpperCase(java.util.Locale.ROOT),true));
        }
        assertEquals("うぁうぃううぇうぉ",input("wha","whi","whu","whe","who"));
    }
}
