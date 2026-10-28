package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GlideDecoderTest {
    @Test public void punctuationLabelsMatchFlickInputInBothModes(){
        String[] bases={"e","u","e","o","o","e","o"};
        int[] directions={2,2,0,0,2,3,3};
        String[] japanese={"、","。","！","？","ー","（","）"};
        String[] english={",",".","!","?","-","(",")"};
        for(int i=0;i<bases.length;i++){
            int index=Arrays.asList(KeyLayout.KEYS).indexOf(bases[i]),direction=directions[i];
            float x=index%3+.5f,y=index/3+.5f;
            var flick=List.of(p(x,y),p(x+(direction==0?-.35f:direction==2?.35f:0),y+(direction==3?.35f:0)));
            assertEquals(english[i],KeyLayout.variant(bases[i],direction,true));
            assertEquals(english[i],GlideDecoder.decode(flick,true).raw());
            assertEquals(japanese[i],KeyLayout.variant(bases[i],direction,false));
            for(int flags=0;flags<16;flags++){
                var style=new JapaneseTypography((flags&1)!=0,(flags&2)!=0,(flags&4)!=0,(flags&8)!=0);
                String label=style.format(KeyLayout.variant(bases[i],direction,false));
                assertEquals(style.format(japanese[i]),label);
                assertEquals(label,style.format(GlideDecoder.decode(flick,false).raw()));
            }
        }
    }
    @Test public void euAndIuCanBeDisabledIndependently(){
        var eu=List.of(p(1.5f,3.5f),p(.5f,3.5f));
        var vowelOnlyEu=List.of(p(1.5f,3.5f),p(1.5f,2.5f),p(.5f,3.5f));
        var iu=List.of(p(.5f,2.5f),p(.5f,3.5f));
        var keu=List.of(p(1.5f,.5f),p(1.5f,3.5f),p(.5f,3.5f));
        var kiu=List.of(p(1.5f,.5f),p(.5f,2.5f),p(.5f,3.5f));
        for(boolean english:new boolean[]{false,true})for(boolean enableEu:new boolean[]{false,true})for(boolean enableIu:new boolean[]{false,true}){
            assertEquals(english?"!":"！",GlideDecoder.decode(eu,english,50,enableEu,enableIu).raw());
            assertEquals("iu",GlideDecoder.decode(iu,english,50,enableEu,enableIu).raw());
            assertEquals(GlideDecoder.decode(vowelOnlyEu,english).raw(),GlideDecoder.decode(vowelOnlyEu,english,50,enableEu,enableIu).raw());
            var decodedEu=GlideDecoder.decode(keu,english,50,enableEu,enableIu);
            var decodedIu=GlideDecoder.decode(kiu,english,50,enableEu,enableIu);
            assertEquals(enableEu?"keu":"ku",decodedEu.raw());
            assertEquals(enableIu?"kiu":"ku",decodedIu.raw());
            assertEquals(enableEu?"K → E → U":"K → U",decodedEu.route());
            assertEquals(enableIu?"K → I → U":"K → U",decodedIu.route());
            assertEquals(enableEu?"けう":"く",Romanizer.convert(decodedEu.raw(),true));
            assertEquals(enableIu?"きう":"く",Romanizer.convert(decodedIu.raw(),true));
        }
    }
    @Test public void disabledEuIuPreserveTapsOtherPairsAndContractedSyllables(){
        var tyiu=List.of(p(2.5f,1.5f),p(.5f,1.5f),p(.9f,2.5f),p(.5f,3.5f));
        var ou=List.of(p(2.5f,3.5f),p(.5f,3.5f));
        for(boolean english:new boolean[]{false,true}){
            assertEquals("tyu",GlideDecoder.decode(tyiu,english,50,false,false).raw());
            assertEquals("ou",GlideDecoder.decode(ou,english,50,false,false).raw());
            for(var tap:List.of(p(.5f,2.5f),p(1.5f,3.5f),p(.5f,3.5f))){
                assertEquals(GlideDecoder.decode(List.of(tap),english).raw(),GlideDecoder.decode(List.of(tap),english,50,false,false).raw());
            }
            var shortELeft=List.of(p(1.5f,3.5f),p(1.2f,3.5f));
            assertEquals(english?"!":"！",GlideDecoder.decode(shortELeft,english,50,true,true).raw());
        }
    }
    @Test public void downwardUFlickTypesApostropheOnlyInEnglish(){
        for(float endY:new float[]{3.85f,4.3f}){
            var flick=List.of(new GlideDecoder.Point(.5f,3.5f),new GlideDecoder.Point(.5f,endY));
            assertEquals("'",GlideDecoder.decode(flick,true).raw());
            assertEquals("～",GlideDecoder.decode(flick,false).raw());
        }
        assertEquals("'",KeyLayout.variant("u",3,true));
    }
    private static GlideDecoder.Point p(float x,float y){return new GlideDecoder.Point(x,y);}
    private static void raw(String expected,GlideDecoder.Point... points){
        for(boolean english:new boolean[]{false,true})assertEquals(expected,GlideDecoder.decode(List.of(points),english).raw());
    }
    @Test public void yoDetoursKeepTheContractedVowelBeforeTheFinalVowel(){
        int[] starts={1,2,4,5,7,8};String[] consonants={"k","h","s","t","n","r"};
        GlideDecoder.Point[] ends={p(.5f,.5f),p(.5f,2.5f),p(.5f,3.5f),p(1.5f,3.5f)};
        for(int s=0;s<starts.length;s++)for(int v=0;v<ends.length;v++){
            var start=p(starts[s]%3+.5f,starts[s]/3+.5f);
            raw(consonants[s]+"yo"+"aiue".charAt(v),start,p(.5f,1.5f),p(2.5f,3.5f),ends[v]);
            // Rounded Y bend lands in I, with an earlier raw sample in Y.
            raw(consonants[s]+"yo"+"aiue".charAt(v),start,p(.65f,1.9f),p(.55f,2.1f),p(2.5f,3.5f),ends[v]);
            raw(consonants[s]+"yo"+"aiue".charAt(v),start,p(.6f,2.05f),p(2.45f,2.98f),ends[v]);
        }
        raw("ka",p(1.5f,.5f),p(.5f,.5f));
        raw("kya",p(1.5f,.5f),p(.5f,1.5f),p(.5f,.5f));
        raw("kau",p(1.5f,.5f),p(.5f,.5f),p(.5f,3.5f));
    }
    @Test public void yiYeAndFExceptionsUseRequestedKana(){
        String[] consonants={"k","h","s","t","n","r","g","z","d","b","p","j","c","q","v"};
        for(String c:consonants){
            assertEquals(c,Romanizer.convert(c+"i",true),Romanizer.convert(c+"yi",true));
            assertEquals(c,Romanizer.convert(c+"i",true)+"ぇ",Romanizer.convert(c+"ye",true));
        }
        int[] starts={1,2,4,5,7,8};String[] plain={"k","h","s","t","n","r"};
        for(int s=0;s<starts.length;s++){
            var start=p(starts[s]%3+.5f,starts[s]/3+.5f);
            raw(plain[s]+"i",start,p(.5f,1.5f),p(.5f,2.5f));
            raw(plain[s]+"ye",start,p(.5f,1.5f),p(1.5f,3.5f));
        }
        raw("fi",p(1.5f,.5f),p(1.5f,.1f),p(.5f,1.5f),p(.5f,2.5f));
        raw("fe",p(1.5f,.5f),p(1.5f,.1f),p(.5f,1.5f),p(1.5f,3.5f));
        assertEquals("ふぃふぇ",Romanizer.convert("fyifye",true));
    }
    @Test public void wyiWyeKeepYAndProduceHistoricalKana(){
        raw("wyi",p(2.5f,2.5f),p(2.9f,2.5f),p(.5f,1.5f),p(.5f,2.5f));
        raw("wye",p(2.5f,2.5f),p(2.9f,2.5f),p(.5f,1.5f),p(1.5f,3.5f));
        assertEquals("ゐゑ",Romanizer.convert("wyiwye",false));
        assertEquals("ゐゑ",Romanizer.convert("WYIWYE",true));
        assertEquals("うぃうぇ",Romanizer.convert("wiwe",true));
        assertEquals("wy",Romanizer.convert("wy",false));
    }
    @Test public void pFlickSurvivesShortAndDeepUpwardTurns(){
        GlideDecoder.Point[] vowels={p(.5f,.5f),p(.5f,2.5f),p(.5f,3.5f),p(1.5f,3.5f),p(2.5f,3.5f)};
        for(int i=0;i<vowels.length;i++){
            String expected="p"+"aiueo".charAt(i);
            raw(expected,p(2.5f,.5f),p(2.48f,.3f),p(2.46f,.33f),vowels[i]);
            raw(expected,p(2.5f,.5f),p(2.5f,-.8f),vowels[i]);
            assertEquals("ぱぴぷぺぽ".substring(i,i+1),Romanizer.convert(expected,true));
            raw("h"+"aiueo".charAt(i),p(2.5f,.5f),vowels[i]);
        }
        raw("ha",p(2.5f,.5f),p(1.8f,.35f),p(.5f,.5f));
        raw("p",p(2.5f,.5f),p(2.5f,.3f));
    }
    @Test public void smallVowelsKeepXAndOnlyTheLastVowel(){
        raw("xa",p(.5f,.5f),p(.5f,.3f),p(.5f,.55f));
        raw("xa",p(.5f,.5f),p(.5f,-.8f),p(.5f,.5f));
        raw("xi",p(.5f,.5f),p(.5f,.1f),p(.5f,.6f),p(.5f,2.5f));
        raw("xu",p(.5f,.5f),p(.5f,.1f),p(.5f,1.5f),p(.5f,3.5f));
        raw("la",p(.5f,1.5f),p(-.1f,1.5f),p(.5f,.5f));
        raw("li",p(.5f,1.5f),p(-.1f,1.5f),p(.5f,.5f),p(.5f,2.5f));
    }
    @Test public void vKeepsTheLastVowelIncludingReturningToU(){
        GlideDecoder.Point[] ends={p(.5f,.5f),p(.5f,2.5f),p(.5f,3.5f),p(1.5f,3.5f),p(2.5f,3.5f)};
        for(int i=0;i<ends.length;i++){
            raw("v"+"aiueo".charAt(i),p(.5f,3.5f),p(.28f,3.5f),p(.6f,3.5f),ends[i]);
            raw("v"+"aiueo".charAt(i),p(.5f,3.5f),p(-.8f,3.5f),ends[i]);
        }
    }
    @Test public void thDhIgnoreHExceptForIAndKeepPendingConsonants(){
        raw("ta",p(2.5f,1.5f),p(2.5f,.5f),p(.5f,.5f));
        raw("da",p(2.5f,1.5f),p(2.9f,1.5f),p(2.5f,.5f),p(.5f,.5f));
        raw("thi",p(2.5f,1.5f),p(2.5f,.5f),p(.5f,2.5f));
        raw("thu",p(2.5f,1.5f),p(2.5f,.5f),p(.5f,3.5f));
        var pending=GlideDecoder.decode(List.of(p(2.5f,1.5f),p(2.5f,.5f)),false).raw();
        assertEquals("th",pending);
        assertEquals("th",Romanizer.convert(pending,false));
        assertEquals("てゃ",Romanizer.convert(pending+"a",false));
        assertEquals("でゃ",Romanizer.convert("dha",false));
        assertEquals("てゃてぃてゅてぇてょ",Romanizer.convert("thathithuthetho",true));
        assertEquals("でゃでぃでゅでぇでょ",Romanizer.convert("dhadhidhudhedho",true));
    }
    @Test public void recordedGalaxyS25GlidesProduceExpectedKana() throws Exception {
        try(BufferedReader in=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/galaxy-s25-glides.txt"),StandardCharsets.UTF_8))){
            String line;while((line=in.readLine())!=null){
                String[] parts=line.split("\\|");List<GlideDecoder.Point> points=new ArrayList<>();
                for(String pair:parts[2].split(";")){String[] xy=pair.split(",");points.add(new GlideDecoder.Point(Float.parseFloat(xy[0]),Float.parseFloat(xy[1])));}
                GlideDecoder.Result result=GlideDecoder.decode(points,false);
                assertEquals(parts[0]+" "+result.route(),parts[1],Romanizer.convert(result.raw(),true));
            }
        }
    }
    @Test public void straightStrokeDoesNotInsertCrossedConsonants(){
        var points=List.of(new GlideDecoder.Point(1.5f,.5f),new GlideDecoder.Point(1,1.5f),new GlideDecoder.Point(.5f,2.5f));
        assertEquals("ki",GlideDecoder.decode(points,false).raw());
    }
    @Test public void sameLayoutTypesEnglishAlternates(){
        assertEquals("q",GlideDecoder.decode(List.of(new GlideDecoder.Point(.5f,2.5f),new GlideDecoder.Point(.1f,2.5f)),true).raw());
        assertEquals("w",GlideDecoder.decode(List.of(new GlideDecoder.Point(2.5f,2.5f),new GlideDecoder.Point(2.9f,2.5f)),true).raw());
        assertEquals(".",GlideDecoder.decode(List.of(new GlideDecoder.Point(.5f,3.5f),new GlideDecoder.Point(1.5f,3.5f)),true).raw());
    }
    @Test public void composingAndExplicitNRemainDistinct(){
        assertEquals("k",Romanizer.convert("k",false));assertEquals("か",Romanizer.convert("ka",false));
        assertEquals("てんき",Romanizer.convert("ten'ki",false));assertEquals("な",Romanizer.convert("na",false));
        assertEquals("がっこう",Romanizer.convert("galtukou",false));
    }
    @Test public void cancelledOrOutOfGridStartDoesNotInventText(){
        assertEquals("",GlideDecoder.decode(List.of(),false).raw());
        assertEquals("",GlideDecoder.decode(List.of(new GlideDecoder.Point(-1,.5f),new GlideDecoder.Point(.5f,.5f)),false).raw());
    }
    @Test public void naNyaBiasChangesOnlyAmbiguousBends(){
        var ambiguous=List.of(new GlideDecoder.Point(1.5f,2.5f),new GlideDecoder.Point(.72f,1.5f),new GlideDecoder.Point(.5f,.5f));
        assertEquals("na",GlideDecoder.decode(ambiguous,false,0).raw());
        assertEquals("nya",GlideDecoder.decode(ambiguous,false,100).raw());
        assertEquals(GlideDecoder.decode(ambiguous,false).raw(),GlideDecoder.decode(ambiguous,false,50).raw());
        var shallow=List.of(new GlideDecoder.Point(1.5f,2.5f),new GlideDecoder.Point(.92f,1.5f),new GlideDecoder.Point(.5f,.5f));
        assertEquals("na",GlideDecoder.decode(shallow,false,50).raw());
        assertEquals("nya",GlideDecoder.decode(shallow,false,100).raw());
        var straight=List.of(new GlideDecoder.Point(1.5f,2.5f),new GlideDecoder.Point(1,1.5f),new GlideDecoder.Point(.5f,.5f));
        var clear=List.of(new GlideDecoder.Point(1.5f,2.5f),new GlideDecoder.Point(.5f,1.5f),new GlideDecoder.Point(.5f,.5f));
        for(int bias:new int[]{0,50,100}){
            assertEquals("na",GlideDecoder.decode(straight,false,bias).raw());
            assertEquals("nya",GlideDecoder.decode(clear,false,bias).raw());
        }
    }
    @Test public void naNyaBiasDoesNotUseLaterYCrossingsOrChangeNyu(){
        var nau=List.of(new GlideDecoder.Point(1.5f,2.5f),new GlideDecoder.Point(.5f,.5f),new GlideDecoder.Point(.5f,1.5f),new GlideDecoder.Point(.5f,3.5f));
        var nyu=List.of(new GlideDecoder.Point(1.5f,2.5f),new GlideDecoder.Point(.5f,1.5f),new GlideDecoder.Point(.5f,3.5f));
        for(int bias:new int[]{0,50,100}){
            assertEquals("nau",GlideDecoder.decode(nau,false,bias).raw());
            assertEquals("nyu",GlideDecoder.decode(nyu,false,bias).raw());
        }
    }
    @Test public void englishAtAndUnderscoreFlicksKeepJapaneseUnchanged(){
        var aRight=List.of(new GlideDecoder.Point(.5f,.5f),new GlideDecoder.Point(.85f,.5f));
        var yRight=List.of(new GlideDecoder.Point(.5f,1.5f),new GlideDecoder.Point(.85f,1.5f));
        assertEquals("@",GlideDecoder.decode(aRight,true).raw());
        assertEquals("_",GlideDecoder.decode(yRight,true).raw());
        assertEquals("a",GlideDecoder.decode(aRight,false).raw());
        assertEquals("y",GlideDecoder.decode(yRight,false).raw());
        assertEquals("@",GlideDecoder.decode(List.of(new GlideDecoder.Point(.5f,.5f),new GlideDecoder.Point(1.1f,.5f)),true).raw());
    }
    @Test public void yIgnoresIAndEBeforeItsVowelButKeepsLaterVowels(){
        var tyiu=List.of(new GlideDecoder.Point(2.5f,1.5f),new GlideDecoder.Point(.5f,1.5f),new GlideDecoder.Point(.9f,2.5f),new GlideDecoder.Point(.5f,3.5f));
        assertEquals("tyu",GlideDecoder.decode(tyiu,false).raw());
        assertEquals("ちゅ",Romanizer.convert(GlideDecoder.decode(tyiu,false).raw(),true));
        var tyeu=List.of(new GlideDecoder.Point(2.5f,1.5f),new GlideDecoder.Point(.5f,1.5f),new GlideDecoder.Point(1.5f,3.5f),new GlideDecoder.Point(.5f,3.5f));
        assertEquals("tyu",GlideDecoder.decode(tyeu,false).raw());
        var kyai=List.of(new GlideDecoder.Point(1.5f,.5f),new GlideDecoder.Point(.5f,1.5f),new GlideDecoder.Point(.5f,.5f),new GlideDecoder.Point(.5f,2.5f));
        assertEquals("kyai",GlideDecoder.decode(kyai,false).raw());
    }
    @Test public void cFlickCanContinueThroughHToU(){
        var chu=List.of(new GlideDecoder.Point(.5f,.5f),new GlideDecoder.Point(-.1f,.5f),new GlideDecoder.Point(2.5f,.5f),new GlideDecoder.Point(.5f,3.5f));
        assertEquals("chu",GlideDecoder.decode(chu,false).raw());
        assertEquals("ちゅ",Romanizer.convert(GlideDecoder.decode(chu,false).raw(),true));
    }
    @Test public void ouIsAVowelPairAndOnlyOeIsQuestionMark(){
        var ou=List.of(new GlideDecoder.Point(2.5f,3.5f),new GlideDecoder.Point(.5f,3.5f));
        var oe=List.of(new GlideDecoder.Point(2.5f,3.5f),new GlideDecoder.Point(1.5f,3.5f));
        assertEquals("ou",GlideDecoder.decode(ou,false).raw());
        assertEquals("おう",Romanizer.convert(GlideDecoder.decode(ou,false).raw(),true));
        assertEquals("？",GlideDecoder.decode(oe,false).raw());
        assertEquals("?",GlideDecoder.decode(oe,true).raw());
    }
    @Test public void upwardJFlickCanContinueToJunWithoutBecomingRun(){
        var jun=List.of(new GlideDecoder.Point(2.5f,2.5f),new GlideDecoder.Point(2.5f,1.2f),new GlideDecoder.Point(.5f,3.5f),new GlideDecoder.Point(1.5f,2.5f));
        var run=List.of(new GlideDecoder.Point(2.5f,2.5f),new GlideDecoder.Point(.5f,3.5f),new GlideDecoder.Point(1.5f,2.5f));
        assertEquals("jun",GlideDecoder.decode(jun,false).raw());
        assertEquals("じゅん",Romanizer.convert(GlideDecoder.decode(jun,false).raw(),true));
        assertEquals("run",GlideDecoder.decode(run,false).raw());
    }
    @Test public void qAndCGlidesSupportEveryRequestedVowelIncludingReturningToOrigin(){
        GlideDecoder.Point[] vowels={new GlideDecoder.Point(.5f,.5f),new GlideDecoder.Point(.5f,2.5f),new GlideDecoder.Point(.5f,3.5f),new GlideDecoder.Point(1.5f,3.5f),new GlideDecoder.Point(2.5f,3.5f)};
        String[][] expected={{"くぁ","くぃ","く","くぇ","くぉ"},{"か","し","く","せ","こ"}};
        for(int group=0;group<2;group++)for(int v=0;v<5;v++){
            float row=group==0?2.5f:.5f;
            var path=List.of(new GlideDecoder.Point(.5f,row),new GlideDecoder.Point(-.1f,row),vowels[v]);
            String raw=(group==0?"q":"c")+"aiueo".charAt(v);
            assertEquals(raw,GlideDecoder.decode(path,false).raw());
            assertEquals(raw,GlideDecoder.decode(path,true).raw());
            assertEquals(raw,expected[group][v],Romanizer.convert(GlideDecoder.decode(path,false).raw(),true));
            assertEquals(raw,expected[group][v],Romanizer.convert(raw.toUpperCase(Locale.ROOT),false));
        }
    }
    @Test public void qAndCUseTheLastVowelAndIgnoreIntermediateVowels(){
        for(boolean english:new boolean[]{false,true}){
            for(int v=0;v<3;v++){
                var end=new GlideDecoder.Point(v==0?.5f:v==1?1.5f:2.5f,3.5f);
                var q=List.of(new GlideDecoder.Point(.5f,2.5f),new GlideDecoder.Point(-.1f,2.5f),new GlideDecoder.Point(.6f,2.4f),end);
                assertEquals("q"+"ueo".charAt(v),GlideDecoder.decode(q,english).raw());
            }
            var qai=List.of(new GlideDecoder.Point(.5f,2.5f),new GlideDecoder.Point(-.1f,2.5f),new GlideDecoder.Point(.5f,.5f),new GlideDecoder.Point(.5f,2.5f));
            var cai=List.of(new GlideDecoder.Point(.5f,.5f),new GlideDecoder.Point(-.1f,.5f),new GlideDecoder.Point(.6f,.6f),new GlideDecoder.Point(.5f,2.5f));
            assertEquals("qi",GlideDecoder.decode(qai,english).raw());
            assertEquals("ci",GlideDecoder.decode(cai,english).raw());
        }
    }
    @Test public void shallowInitialRightFlickIsRetainedForMZG(){
        String[] bases={"m","z","g"};float[] rows={2.5f,1.5f,.5f};
        for(int i=0;i<rows.length;i++){
            var path=List.of(new GlideDecoder.Point(1.5f,rows[i]),new GlideDecoder.Point(1.7f,rows[i]+.02f),new GlideDecoder.Point(1.75f,rows[i]+.08f),new GlideDecoder.Point(.5f,3.5f));
            assertEquals(bases[i]+"u",GlideDecoder.decode(path,false).raw());
        }
        assertEquals("no",GlideDecoder.decode(List.of(new GlideDecoder.Point(1.5f,2.5f),new GlideDecoder.Point(1.7f,2.7f),new GlideDecoder.Point(2.5f,3.5f)),false).raw());
        assertEquals("ko",GlideDecoder.decode(List.of(new GlideDecoder.Point(1.5f,.5f),new GlideDecoder.Point(1.6f,.8f),new GlideDecoder.Point(2.5f,3.5f)),false).raw());
    }
    @Test public void unconvertibleModifiersAreIgnoredButValidModifiersAreKept(){
        var cya=List.of(new GlideDecoder.Point(.5f,.5f),new GlideDecoder.Point(-.1f,.5f),new GlideDecoder.Point(.5f,1.5f),new GlideDecoder.Point(.5f,.5f));
        assertEquals("ca",GlideDecoder.decode(cya,false).raw());
        assertEquals("か",Romanizer.convert(GlideDecoder.decode(cya,false).raw(),true));
        assertFalse(Romanizer.isSyllable("cya"));
        assertTrue(Romanizer.isSyllable("cye"));
        assertTrue(Romanizer.isSyllablePrefix("ch"));
    }
}
