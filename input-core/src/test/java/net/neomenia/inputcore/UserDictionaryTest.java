package net.neomenia.inputcore;

import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class UserDictionaryTest {
    private UserDictionary read(String value)throws IOException {return UserDictionary.read(new StringReader(value));}
    @Test public void godanRowsUseTheirOwnSoundChanges()throws Exception {
        String[][] cases={{"ワ","かう","買う","かった","買った","かわない","買わない"},{"カ","かく","描く","かいて","描いて","かかない","描かない"},{"ガ","およぐ","泳ぐ","およいだ","泳いだ","およがない","泳がない"},{"サ","はなす","話す","はなした","話した","はなさない","話さない"},{"タ","まつ","待つ","まって","待って","またない","待たない"},{"ナ","しぬ","死ぬ","しんだ","死んだ","しなない","死なない"},{"バ","あそぶ","遊ぶ","あそんで","遊んで","あそばない","遊ばない"},{"マ","よむ","詠む","よんだ","詠んだ","よまない","詠まない"},{"ラ","ぐぐる","ググる","ぐぐった","ググった","ぐぐらない","ググらない"}};
        for(String[] c:cases){UserDictionary dictionary=read(c[1]+"\t"+c[2]+"\t動詞"+c[0]+"行五段\n");assertEquals(c[0],List.of(c[4]),dictionary.exact(c[3]));assertEquals(c[0],List.of(c[6]),dictionary.exact(c[5]));}
    }
    @Test public void ichidanIrregularVerbsAndAdjectivesInflect()throws Exception {
        UserDictionary dictionary=read("たべる\t喰べる\t動詞一段\nてすとする\tテストする\t動詞サ変\nくる\t来る\t動詞カ変\nくる\tくる\t動詞カ変\nろんずる\t論ずる\t動詞ザ変\nえもい\tエモい\t形容詞\nしずか\t閑か\t名詞形動\nてすと\tテスト\t名詞サ変\nいく\t行く\t動詞カ行五段\n");
        for(String[] c:new String[][]{{"たべました","喰べました"},{"たべられない","喰べられない"},{"たべさせられた","喰べさせられた"},{"てすとした","テストした"},{"てすとできる","テストできる"},{"ろんじた","論じた"},{"ろんじません","論じません"},{"えもかった","エモかった"},{"えもくない","エモくない"},{"しずかな","閑かな"},{"しずかでした","閑かでした"},{"いった","行った"},{"いっている","行っている"}})assertTrue(c[0]+dictionary.exact(c[0]),dictionary.exact(c[0]).contains(c[1]));
        assertEquals(List.of("来ない","こない"),dictionary.exact("こない"));assertEquals(List.of("来た","きた"),dictionary.exact("きた"));assertEquals(List.of("来れば","くれば"),dictionary.exact("くれば"));assertEquals(List.of("来られた","こられた"),dictionary.exact("こられた"));
        assertFalse(dictionary.exact("いきた").contains("行きた"));
    }
    @Test public void inflectedSentencesKeepAuxiliariesAndRespectLongestEntries()throws Exception {
        UserDictionary dictionary=read("ぐぐる\tググる\t動詞ラ行五段\nぐぐった\t検索済み\t名詞\nたべる\t喰べる\t動詞一段\n");
        assertEquals(List.of("ググった","検索済み"),dictionary.exact("ぐぐった"));
        assertEquals(List.of("ググった結果","検索済み結果"),dictionary.candidates("ぐぐったけっか",(part,count)->List.of("結果"),()->false));
        assertEquals(List.of("喰べました"),dictionary.candidates("たべました",(part,count)->List.of("誤変換"),()->false));
        assertTrue(dictionary.candidates("ぐぐらない",(part,count)->List.of(part),()->true).isEmpty());
    }
    @Test public void importedStemsRoundTripWithoutSavingExpandedForms()throws Exception {
        UserDictionary dictionary=read("ぐぐ\tググ\t動詞ラ行五段\tコメント\nたべ\t喰べ\t動詞一段\nえも\tエモ\t形容詞\n");
        assertEquals(List.of("ググった"),dictionary.exact("ぐぐった"));assertEquals(List.of("喰べない"),dictionary.exact("たべない"));assertEquals(List.of("エモくて"),dictionary.exact("えもくて"));
        StringWriter out=new StringWriter();dictionary.write(out);assertEquals(3,out.toString().lines().count());assertEquals(dictionary.entries(),read(out.toString()).entries());
    }
    @Test public void exclusionsApplyToGeneratedFormsAndUnknownPartsOfSpeechStayLiteral()throws Exception {
        UserDictionary dictionary=read("ぐぐる\tググる\t動詞ラ行五段\nぐぐった\tググった\t抑制単語\nたべる\t喰べる\t独自品詞\n");
        assertTrue(dictionary.exact("ぐぐった").isEmpty());assertEquals(List.of("ググらない"),dictionary.exact("ぐぐらない"));assertTrue(dictionary.exact("たべた").isEmpty());assertEquals(List.of("喰べる"),dictionary.exact("たべる"));
        assertTrue(read("する\tする\t動詞サ変\n").exact("").isEmpty());
    }
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
