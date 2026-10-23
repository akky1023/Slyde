package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ConversionPlanTest {
    @Test public void chunksPreserveEveryCharacterAndBoundModelInput(){
        String input="きょうはいいてんきです。".repeat(20)+" URL https://example.com 😀（１２３）";
        StringBuilder restored=new StringBuilder();
        for(var part:ConversionPlan.split(input,128)){restored.append(part.text());assertEquals(restored.length(),part.end());if(part.convert())assertTrue(part.text().codePointCount(0,part.text().length())<=128);}
        assertEquals(input,restored.toString());
    }
    @Test public void unpunctuatedLongReadingDoesNotGetDropped(){
        String input="あしたのかいぎでつかうしりょうをかくにんしてめーるでおくってください".repeat(8);
        List<ConversionPlan.Part> parts=ConversionPlan.split(input,128);assertTrue(parts.size()>1);
        assertEquals(input,String.join("",parts.stream().map(ConversionPlan.Part::text).toArray(String[]::new)));
    }
    @Test public void literalsKeepOrderWhileChunkAlternativesAreCombined(){
        List<String> values=ConversionPlan.combine(List.of(List.of("今日は","京は"),List.of("。URL😀 "),List.of("学校","学工")),3);
        assertEquals("今日は。URL😀 学校",values.get(0));assertEquals(3,values.size());assertEquals(3,new HashSet<>(values).size());
        for(String value:values)assertTrue(value.contains("。URL😀 "));
    }
    @Test public void contextsDoNotSplitSurrogatePairs(){assertEquals("😀語",ConversionPlan.tail("日本😀語",2));assertEquals("日😀",ConversionPlan.head("日😀本",2));}
    @Test public void generatedBoundaryNewlinesAreAcceptedButExplanatoryLinesAreNot(){
        assertEquals("明日の会議",ZenzCandidates.normalize("\n\n明日の会議\r\n"));
        assertTrue(ZenzCandidates.safe(ZenzCandidates.normalize("\n明日の会議\n")));
        assertFalse(ZenzCandidates.safe(ZenzCandidates.normalize("明日の会議\n説明")));
    }
}
