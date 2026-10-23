package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class ZenzCandidatesTest {
    @Test public void rejectsProtocolAndBrokenOutput(){
        for(String value:new String[]{""," ","\uee00日本","日本\uee0f","日\n本","\ufffd","\ud800"})assertFalse(value,ZenzCandidates.safe(value));
        assertTrue(ZenzCandidates.safe("今日はいい天気です。"));assertTrue(ZenzCandidates.safe("日本😀"));assertFalse(ZenzCandidates.safe(null));
    }
    @Test public void addsAiWithoutLosingOrDuplicatingDictionaryChoices(){
        assertEquals(List.of("日本語","日本","にほんご"),ZenzCandidates.merge(List.of("日本","日本語","にほんご"),"日本語"));
        assertEquals(List.of("日本","にほん"),ZenzCandidates.merge(List.of("日本","にほん"),"\uee00"));
    }
}
