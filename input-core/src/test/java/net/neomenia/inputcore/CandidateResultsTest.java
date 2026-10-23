package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class CandidateResultsTest {
    @Test public void aiCannotReplaceTheBestCompletedDictionarySentence(){
        for(boolean aiFirst:new boolean[]{true,false}){
            CandidateResults results=new CandidateResults(List.of("あしたのかいぎ"));
            if(aiFirst)results.ai("明日のかいぎ");
            results.dictionary(List.of("明日の会議","明日の懐疑"));
            if(!aiFirst)results.ai("明日のかいぎ");
            assertEquals(List.of("明日の会議","明日のかいぎ","明日の懐疑"),results.values());
        }
        CandidateResults pending=new CandidateResults(List.of("あしたのかいぎ"));pending.ai("明日の会議");assertEquals("明日の会議",pending.values().get(0));
    }
    @Test public void parallelResultsKeepAiRegardlessOfArrivalOrder(){
        for(boolean aiFirst:new boolean[]{false,true}){
            CandidateResults results=new CandidateResults(List.of("にほんご","ニホンゴ"));
            if(aiFirst)results.ai("日本語");results.dictionary(List.of("日本語","日本","にほんご"));if(!aiFirst)results.ai("日本語");
            assertEquals(List.of("日本語","日本","にほんご"),results.values());assertEquals("日本語",results.ai());
        }
    }
    @Test public void invalidAiKeepsImmediateReadingAndDictionary(){
        CandidateResults results=new CandidateResults(List.of("きょう","キョウ"));results.ai("\uee00");
        assertEquals(List.of("きょう","キョウ"),results.values());assertNull(results.ai());results.dictionary(List.of("今日","京"));assertEquals(List.of("今日","京"),results.values());
    }
    @Test public void cacheEvictsLeastRecentlyUsedAndSnapshotsResults(){
        CandidateCache cache=new CandidateCache(2);List<String> source=new ArrayList<>(List.of("日本"));cache.put("にほん",source);source.clear();
        cache.put("きょう",List.of("今日"));assertEquals(List.of("日本"),cache.get("にほん"));cache.put("がっこう",List.of("学校"));
        assertNull(cache.get("きょう"));assertEquals(List.of("日本"),cache.get("にほん"));
        try{cache.get("にほん").clear();fail("cached results must be immutable");}catch(UnsupportedOperationException expected){}
    }
}
