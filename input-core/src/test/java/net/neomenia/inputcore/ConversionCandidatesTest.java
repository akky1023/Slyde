package net.neomenia.inputcore;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConversionCandidatesTest {
    @Test public void bundledMozcProvidesSymbolsAndEmojiByReading() throws Exception {
        ConversionCandidates source=new ConversionCandidates();
        for(String[] entry:new String[][]{{"やじるし","→"},{"ほし","★"},{"かっこ","【】"},{"はーと","♥\ufe0e"},{"むげん","∞"},{"おんぷ","♪"},{"えん","￥"},{"ねこ","🐱"}})
            assertTrue(Arrays.toString(entry),source.symbols(entry[0]).contains(entry[1]));
        assertEquals(source.symbols("やじるし"),source.symbols("ヤジルシ"));
        assertEquals(source.symbols("はーと"),source.symbols("ﾊｰﾄ"));
        assertTrue(source.symbols("きごう").size()>100);
    }

    @Test public void translatedNamesAndConventionalReadingsReachFormerBoardSymbols() throws Exception {
        ConversionCandidates source=new ConversionCandidates();
        for(String[] entry:new String[][]{{"れいわ","㋿"},{"へいせい","㍻"},{"まるにじゅういち","㉑"},{"まるごじゅう","㊿"},{"てんじいちにさん","⠇"},{"こくばんふとじおおもじえぬ","ℕ"},{"にほんこっき","🇯🇵"},{"ひらきかっこ","（"},{"のっといこーる","≠"},{"まいくろあんぺあ","㎂"}})
            assertTrue(Arrays.toString(entry),source.symbols(entry[0]).contains(entry[1]));
        assertEquals(List.of("（）","()","「」","『』","【】"),source.symbols("かっこ").subList(0,5));
        assertTrue(source.symbols("はーと").contains("♥\ufe0e"));
        assertFalse(source.symbols("はーと").contains("♥"));
    }

    @Test public void everyFormerBoardEntryHasAnExactJapaneseReading() throws Exception {
        ConversionCandidates source=new ConversionCandidates();
        Set<String> covered=new HashSet<>();
        SymbolPresentation presentation=SymbolPresentation.load();
        try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/conversion/unicode-symbols.tsv"),java.nio.charset.StandardCharsets.UTF_8))){
            reader.readLine();String line;
            while((line=reader.readLine())!=null){
                String[] fields=line.split("\t",-1);assertEquals(6,fields.length);
                String reading=fields[2].split(" ")[0];assertTrue(reading,reading.matches("[ぁ-ゖー]+"));
                assertTrue(fields[3],source.symbols(reading).contains(presentation.apply(fields[1],fields[0].equals("EMOJI"))));
                assertFalse(fields[3],fields[3].contains("<"));
                covered.add(fields[1]);
            }
        }
        for(var category:SymbolCatalog.load())for(var entry:category.entries())assertTrue(category.name()+" "+entry.value(),covered.contains(entry.value()));
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
