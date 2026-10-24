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

    @Test public void translatedNamesAndConventionalReadingsReachFormerBoardSymbols() throws Exception {
        ConversionCandidates source=new ConversionCandidates();
        for(String[] entry:new String[][]{{"れいわ","㋿"},{"へいせい","㍻"},{"まるにじゅういち","㉑"},{"まるごじゅう","㊿"},{"てんじいちにさん","⠇"},{"こくばんふとじおおもじえぬ","ℕ"},{"にほんこっき","🇯🇵"},{"ひらきかっこ","（"},{"のっといこーる","≠"},{"まいくろあんぺあ","㎂"}})
            assertTrue(Arrays.toString(entry),source.symbols(entry[0]).contains(entry[1]));
        assertEquals(List.of("（）","()","「」","｢｣","『』","【】"),source.symbols("かっこ").subList(0,6));
        assertTrue(source.symbols("はーと").contains("♥"));
        assertFalse(source.symbols("はーと").contains("♥\ufe0e"));
    }

    @Test public void conventionalReadingsOfferBothWidthsInVisibleConversionCandidates() throws Exception {
        ConversionCandidates source=new ConversionCandidates();
        for(String[] sample:new String[][]{{"いげた","＃","#"},{"なんばー","＃","#"},{"はっしゅ","＃","#"},{"あっと","＠","@"},{"えん","￥","¥"},{"どる","＄","$"},{"ぱーせんと","％","%"},{"ひらきかっこ","（","("},{"かぎかっこ","「」","｢｣"},{"みぎやじるし","→","￫"}}){
            List<String> candidates=source.convert(sample[0],(reading,count)->List.of(reading),()->false);
            assertTrue(Arrays.toString(sample),candidates.contains(sample[1]));
            assertTrue(Arrays.toString(sample),candidates.contains(sample[2]));
        }
        assertTrue(source.symbols("えん").contains("¥"));
        assertFalse(source.symbols("にほんこっき").contains("ＪＰ"));
        assertFalse(source.symbols("まるにじゅういち").contains("21"));
        assertFalse(source.symbols("まいくろあんぺあ").contains("μA"));
        assertFalse(source.symbols("しゃーぷ").contains("＃\u20e3"));
    }

    @Test public void everyDictionaryReadingComplementsExplicitUnicodeWidthPairs() throws Exception {
        ConversionCandidates source=new ConversionCandidates();Map<Integer,Integer> widths=new HashMap<>();
        try(var reader=resource("width-variants.tsv")){
            String line;while((line=reader.readLine())!=null){
                if(line.startsWith("#")||line.isBlank())continue;
                String[] fields=line.split("\t");int wide=Integer.parseInt(fields[0],16),narrow=Integer.parseInt(fields[1],16);
                widths.put(wide,narrow);widths.put(narrow,wide);
                assertEquals(java.text.Normalizer.normalize(new String(Character.toChars(wide)),java.text.Normalizer.Form.NFKC),java.text.Normalizer.normalize(new String(Character.toChars(narrow)),java.text.Normalizer.Form.NFKC));
            }
        }
        assertEquals(116*2,widths.size());int checked=0;
        for(String file:List.of("unicode-symbols.tsv","symbol.tsv","emoji_data.tsv"))try(var reader=resource(file)){
            String line;while((line=reader.readLine())!=null){
                if(line.startsWith("#")||line.startsWith("POS\t")||line.isBlank())continue;
                String[] fields=line.split("\t",-1);String value=ConversionCandidates.plainSymbol(fields[1]);
                if(value.isEmpty()||!value.codePoints().allMatch(widths::containsKey))continue;
                StringBuilder alternate=new StringBuilder();value.codePoints().forEach(cp->alternate.appendCodePoint(widths.get(cp)));
                for(String reading:fields[2].trim().split("\\s+"))if(!reading.isEmpty()){
                    List<String> values=source.symbols(reading);
                    assertTrue(file+" "+reading+" "+value,values.contains(value));
                    assertTrue(file+" "+reading+" "+alternate,values.contains(alternate.toString()));
                    assertEquals(values.size(),new HashSet<>(values).size());checked++;
                }
            }
        }
        assertTrue("Audit should include all dictionaries' symbol aliases",checked>500);
    }

    private static java.io.BufferedReader resource(String name){
        return new java.io.BufferedReader(new java.io.InputStreamReader(ConversionCandidatesTest.class.getResourceAsStream("/conversion/"+name),java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test public void everyBaseEntryHasAnExactJapaneseReading() throws Exception {
        ConversionCandidates source=new ConversionCandidates();
        Set<String> covered=new HashSet<>();
        try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/conversion/unicode-symbols.tsv"),java.nio.charset.StandardCharsets.UTF_8))){
            reader.readLine();String line;
            while((line=reader.readLine())!=null){
                String[] fields=line.split("\t",-1);assertEquals(6,fields.length);
                String reading=fields[2].split(" ")[0];assertTrue(reading,reading.matches("[ぁ-ゖー]+"));
                assertTrue(fields[3],source.symbols(reading).contains(fields[1]));
                assertEquals(fields[1],ConversionCandidates.plainSymbol(fields[1]));
                assertFalse(fields[3],fields[3].contains("<"));
                covered.add(fields[1]);
            }
        }
        for(var category:SymbolCatalog.load())for(var entry:category.entries()){
            String value=ConversionCandidates.plainSymbol(entry.value());
            if(!value.isEmpty())assertTrue(category.name()+" "+value,covered.contains(value));
        }
    }

    @Test public void plainSymbolsExcludePresentationAndAppearanceVariantsFromAllDictionaries() throws Exception {
        ConversionCandidates source=new ConversionCandidates();
        assertTrue(source.symbols("しゃーぷ").contains("#"));
        assertFalse(source.symbols("しゃーぷ").contains("#\ufe0e"));
        for(String reading:List.of("しゃーぷ","はーと","て","ひと","じょせい","だんせい","あかげ","はだいろ")){
            List<String> values=source.symbols(reading);
            for(String value:values)assertEquals(reading,value,ConversionCandidates.plainSymbol(value));
            assertEquals(values.size(),new HashSet<>(values).size());
        }
        assertTrue(source.symbols("て").contains("✋"));
        assertEquals("",ConversionCandidates.plainSymbol("👍🏽"));
        assertEquals("",ConversionCandidates.plainSymbol("👩‍🦰"));
        assertEquals("",ConversionCandidates.plainSymbol("🦱"));
        assertEquals("👨‍👩‍👧",ConversionCandidates.plainSymbol("👨‍👩‍👧"));
        assertEquals("🇯🇵",ConversionCandidates.plainSymbol("🇯🇵"));
        assertEquals("#\u20e3",ConversionCandidates.plainSymbol("#\ufe0f\u20e3"));
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

    @Test public void symbolOnlyWordsInPunctuationSeparatedSentencesFollowNormalWords() throws Exception {
        ConversionCandidates source=new ConversionCandidates();
        assertEquals(List.of("括弧。","カッコ。","()。"),source.convert("かっこ。",(part,count)->List.of("()","括弧","カッコ"),()->false));
        assertEquals(List.of("はーと。","♥。"),source.convert("はーと。",(part,count)->List.of("♥"),()->false));
        for(String input:List.of("(",")","（","）","()","（）")){
            List<String> values=source.convert(input,(part,count)->{fail("Literal bracket must not enter the kana engine");return List.of();},()->false);
            boolean opening=input.equals("(")||input.equals("（");
            List<String> expectedValues=input.length()==2?List.of("()","（）","[]","［］","{}","｛｝","｢｣","「」"):opening?List.of("[","［"):List.of("]","］");
            for(String expected:expectedValues){
                assertTrue(input+" -> "+expected,values.contains(expected));
            }
        }
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
