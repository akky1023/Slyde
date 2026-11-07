package net.neomenia.inputcore;

import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class UserDictionaryTest {
    private UserDictionary read(String value)throws IOException {return UserDictionary.read(new StringReader(value));}
    @Test public void mozcTextRoundTripsCommentsAndNormalizesReadings()throws Exception {
        UserDictionary dictionary=read("\ufeff# exported\r\nｽﾗｲﾄﾞ\tSlyde\t固有名詞\tメモ\r\nすらいど\tSlyde\t固有名詞\tメモ\r\nほし\t🌟\t名詞\r\n");
        assertEquals(2,dictionary.entries().size());assertEquals(List.of("Slyde"),dictionary.exact("スライド"));
        StringWriter out=new StringWriter();dictionary.write(out);assertEquals(dictionary.entries(),read(out.toString()).entries());
    }
    @Test public void malformedImportFailsWithLineNumberAndDoesNotSilentlyDropEntries()throws Exception {
        for(String invalid:List.of("よみ\t単語","\t単語\t名詞","\t\t抑制単語","よみ\t単語\t名詞\tコメント\t余分")){
            try{read("よみ\t単語\t名詞\n"+invalid);fail(invalid);}catch(IOException e){assertTrue(e.getMessage(),e.getMessage().startsWith("2行目"));}
        }
    }
    @Test public void mergeRetainsExistingEntriesAndDeduplicates()throws Exception {
        UserDictionary first=read("よみ\t一\t名詞\n");
        UserDictionary merged=first.merge(read("よみ\t一\t名詞\nよみ\t二\t名詞\n"));
        assertEquals(List.of("一","二"),merged.exact("よみ"));assertEquals(1,first.entries().size());
    }
    @Test public void customWordsCoverWholeSentenceAndPreferLongestReading()throws Exception {
        UserDictionary dictionary=read("すらいど\tSlyde\t名詞\nすらいど\tSLYDE\t名詞\nすら\t短い\t名詞\n");
        Map<String,String> base=Map.of("でにゅうりょく","で入力");
        List<String> candidates=dictionary.candidates("すらいどでにゅうりょく",(part,count)->List.of(base.getOrDefault(part,part)),()->false);
        assertEquals(List.of("Slydeで入力","SLYDEで入力"),candidates);
        assertEquals(List.of("Slyde","SLYDE"),dictionary.candidates("すらいど",(part,count)->List.of(part),()->false));
        assertTrue(dictionary.candidates("すらいど",(part,count)->List.of(part),()->true).isEmpty());
    }
    @Test public void sentenceAlternativesAreBounded()throws Exception {
        UserDictionary dictionary=read("あ\tA\t名詞\nあ\tB\t名詞\n");
        List<String> values=dictionary.candidates("あ".repeat(20),(part,count)->List.of(part),()->false);
        assertEquals(32,values.size());assertTrue(values.stream().allMatch(value->value.length()==20));
    }
    @Test public void exclusionOverridesOrdinaryRegistrationAndDemotionRegardlessOfRowOrder()throws Exception {
        UserDictionary dictionary=read("はし\t橋\t名詞\nはし\t箸\t名詞\nはし\t橋\t抑制\nはし\t橋\t除外\n");
        assertEquals(List.of("箸"),dictionary.exact("はし"));assertTrue(dictionary.isExcluded("ハシ","橋"));assertFalse(dictionary.isExcluded("はし","箸"));
        assertEquals(List.of("箸","端"),dictionary.policy("はし",value->value).apply(List.of("橋","箸","端")));
        StringWriter out=new StringWriter();dictionary.write(out);assertTrue(out.toString().contains("橋\t抑制単語"));assertEquals(dictionary.entries(),read(out.toString()).entries());
    }
    @Test public void demotionKeepsCandidatesInStableOrderAndDoesNotRegisterNewWords()throws Exception {
        UserDictionary dictionary=read("はし\t橋\t抑制\nはし\t端\t抑制\n");
        assertTrue(dictionary.exact("はし").isEmpty());
        assertEquals(List.of("箸","はし","橋","端"),dictionary.policy("はし",value->value).apply(List.of("橋","箸","端","はし")));
        assertEquals(List.of("橋","箸"),dictionary.policy("べつ",value->value).apply(List.of("橋","箸")));
    }
    @Test public void policySupportsEmptyReadingOrWordButNeverBoth()throws Exception {
        UserDictionary dictionary=read("\t橋\t抑制単語\nはし\t\t抑制\n");
        assertTrue(dictionary.isExcluded("きょう","橋"));assertTrue(dictionary.isDemoted("はし","箸"));
        assertEquals(List.of("箸","端"),dictionary.policy("はし",value->value).apply(List.of("橋","箸","端")));
        UserDictionary all=read("はし\t\t抑制単語\n");assertTrue(all.policy("はし",value->value).apply(List.of("橋","箸","はし")).isEmpty());
    }
    @Test public void typographyCannotReintroduceExcludedWords()throws Exception {
        UserDictionary dictionary=read("てん\t，\t抑制単語\nてん\t．\t抑制\n");
        var policy=dictionary.policy("てん",value->value.replace("，",",").replace("．","."));
        assertEquals(List.of("点","."),policy.apply(List.of(",",".","点","，")));
    }
}
