package net.neomenia.inputcore;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConversionCandidatesTest {
    @Test public void bundledMozcProvidesSymbolsAndEmojiByReading() throws Exception {
        ConversionCandidates source=new ConversionCandidates();
        for(String[] entry:new String[][]{{"やじるし","→"},{"ほし","★"},{"かっこ","【】"},{"はーと","♥"},{"むげん","∞"},{"おんぷ","♪"},{"えん","￥"},{"ねこ","🐱"}})
            assertTrue(Arrays.toString(entry),source.symbols(entry[0]).contains(entry[1]));
        assertEquals(source.symbols("やじるし"),source.symbols("ヤジルシ"));
        assertEquals(source.symbols("はーと"),source.symbols("ﾊｰﾄ"));
        assertTrue(source.symbols("きごう").size()>100);
    }

    @Test public void moreThanTwelveCandidatesKeepRankingAndExposeSymbolsEarly() throws Exception {
        ConversionCandidates source=new ConversionCandidates();
        List<String> words=new ArrayList<>();for(int i=0;i<100;i++)words.add("候補"+i);
        List<String> values=source.convert("ほし",(part,count)->{assertEquals(64,(int)count);return words;},()->false);
        assertEquals(words.subList(0,8),values.subList(0,8));
        assertEquals(source.symbols("ほし").get(0),values.get(8));
        assertTrue(values.contains("候補99"));
        assertEquals(values.size(),new HashSet<>(values).size());
    }

    @Test public void symbolsRequireWholeReadingAndLiteralsSurvive() throws Exception {
        ConversionCandidates source=new ConversionCandidates();
        assertEquals(List.of("星が見える"),source.convert("ほしがみえる",(part,count)->List.of("星が見える"),()->false));
        assertEquals(List.of("日本😀 https://example.com"),source.convert("にほん😀 https://example.com",(part,count)->List.of("日本"),()->false));
        assertTrue(source.convert("?",(part,count)->{fail("Literal must not enter kana engine");return List.of();},()->false).contains("？"));
        assertTrue(source.convert("ほし",(part,count)->{fail("Cancelled");return List.of();},()->true).isEmpty());
        assertTrue(source.convert("",(part,count)->{fail("Empty");return List.of();},()->false).isEmpty());
    }

    @Test public void longSentencesRetainSmallSearchBudgetAndCancellation() throws Exception {
        ConversionCandidates source=new ConversionCandidates();
        String reading="あしたのかいぎでつかうしりょうをかくにんしてめーるでおくってください";
        assertEquals(List.of("明日の会議で使う資料を確認してメールで送ってください"),source.convert(reading,(part,count)->{
            assertEquals(12,(int)count);return List.of("明日の会議で使う資料を確認してメールで送ってください");
        },()->false));
        boolean[] cancelled={false};
        assertTrue(source.convert("ほし",(part,count)->{cancelled[0]=true;return List.of("星");},()->cancelled[0]).isEmpty());
    }

    @Test public void broadReadingsHaveBoundedUiCost() throws Exception {
        ConversionCandidates source=new ConversionCandidates();
        List<String> words=new ArrayList<>();for(int i=0;i<1000;i++)words.add("候補"+i);
        List<String> values=source.convert("きごう",(part,count)->words,()->false);
        assertEquals(ConversionCandidates.MAX_CANDIDATES,values.size());
        assertEquals(words.subList(0,8),values.subList(0,8));
        assertTrue(values.containsAll(source.symbols("きごう").subList(0,Math.min(192,source.symbols("きごう").size()))));
    }
}
