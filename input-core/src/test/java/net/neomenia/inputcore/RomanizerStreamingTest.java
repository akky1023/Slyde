package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;

public class RomanizerStreamingTest {
    private String input(String... taps){String state="";for(String tap:taps)state=Romanizer.append(state,tap);return state;}
    @Test public void repeatedWRemainsAlphabetWhileTypingAndOnConfirmation(){
        String state="";
        for(int count=1;count<=8;count++){
            state=Romanizer.append(state,"w");String expected="w".repeat(count);
            assertEquals(expected,state);
            assertEquals(expected,Romanizer.convert(expected,false));
            assertEquals(expected,Romanizer.convert(expected,true));
            assertEquals(expected,Romanizer.convert(expected.toUpperCase(java.util.Locale.ROOT),true));
        }
        assertEquals("わらwwww",input("wa","ra","w","w","w","w"));
    }
    @Test public void repeatedWStillAllowsWSyllablesAndOtherGeminateConsonants(){
        String[] kana={"わ","うぃ","う","うぇ","を"};
        for(int count=2;count<=8;count++)for(int i=0;i<5;i++){
            String vowel="aiueo".substring(i,i+1),expected="w".repeat(count-2)+"っ"+kana[i];
            String state="";for(int n=0;n<count;n++)state=Romanizer.append(state,"w");
            assertEquals(expected,Romanizer.append(state,vowel));
            assertEquals(expected,Romanizer.convert("w".repeat(count)+vowel,false));
            assertEquals(expected,Romanizer.convert("w".repeat(count)+vowel,true));
            assertEquals(expected,Romanizer.convert(expected,true));
        }
        assertEquals("wwっわ",input("w","w","w","wa"));
        assertEquals("wwwうぃ",input("w","w","w","w","h","i"));
        assertEquals("っっっk",input("k","k","k","k"));
        assertEquals("っっっか",input("k","k","k","k","a"));
    }
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
