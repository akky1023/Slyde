package net.neomenia.inputcore;

import java.util.*;

/** Shared suffix tables keep inflected dictionaries proportional to registered stems. */
final class UserDictionaryInflections {
    record Form(String reading,String word) {}
    record Rule(String readingEnding,String wordEnding,List<Form> forms) {}
    record Word(String stem,Rule rule) {}
    private static final Map<String,Rule> RULES=new LinkedHashMap<>();
    static {
        godan("ワ","う","わ","い","え","お","って","った");
        godan("カ","く","か","き","け","こ","いて","いた");
        godan("ガ","ぐ","が","ぎ","げ","ご","いで","いだ");
        godan("サ","す","さ","し","せ","そ","して","した");
        godan("タ","つ","た","ち","て","と","って","った");
        godan("ナ","ぬ","な","に","ね","の","んで","んだ");
        godan("バ","ぶ","ば","び","べ","ぼ","んで","んだ");
        godan("マ","む","ま","み","め","も","んで","んだ");
        godan("ラ","る","ら","り","れ","ろ","って","った");
        List<Form> ichidan=new ArrayList<>();ichidan(ichidan,"");
        put("動詞一段","る","る",ichidan);
        List<Form> suru=new ArrayList<>();
        same(suru,"する","し","しない","しなかった","しなくて","しなく","しなければ","します","しました","しません","しませんでした","して","した","したら","したり","している","していた","しています","していない","すれば","しろ","せよ","しよう");
        ichidan(suru,"され");ichidan(suru,"させ");ichidan(suru,"でき");
        put("動詞サ変","する","する",suru);
        List<Form> zuru=new ArrayList<>();
        same(zuru,"ずる","ずれば","ぜず","ぜぬ","ぜよ");
        ichidan(zuru,"じ");ichidan(zuru,"じられ");ichidan(zuru,"じさせ");ichidan(zuru,"ぜられ");
        put("動詞ザ変","ずる","ずる",zuru);
        List<Form> kuru=new ArrayList<>();
        for(Form form:ichidan){
            String suffix=form.reading();
            String reading=suffix.equals("る")||suffix.equals("れば")?"く"+suffix:suffix.equals("ろ")?"こい":suffix.startsWith("られ")||suffix.startsWith("させ")||suffix.startsWith("な")||suffix.equals("よう")||suffix.equals("よ")?"こ"+suffix:"き"+suffix;
            kuru.add(new Form(reading,"来"+(suffix.equals("ろ")?"い":suffix)));
        }
        put("動詞カ変","くる","来る",kuru);
        List<Form> adjective=new ArrayList<>();
        same(adjective,"い","く","くない","くなかった","くなく","くなくて","くなければ","くて","かった","かったら","かったり","ければ","かろう","さ","そう");
        put("形容詞","い","い",adjective);
        List<Form> adjectival=new ArrayList<>();
        same(adjectival,"","な","に","だ","だった","だったら","だったり","だろう","なら","ならば","で","では","ではない","ではなかった","じゃない","じゃなかった","です","でした","ではありません","ではありませんでした");
        put("形容動詞","","",adjectival);RULES.put("名詞形動",RULES.get("形容動詞"));
        RULES.put("名詞サ変",new Rule("","",suru));
    }
    static List<String> partsOfSpeech(){
        List<String> result=new ArrayList<>(List.of("名詞","固有名詞","人名","姓","名","組織","地名"));
        result.addAll(RULES.keySet());result.addAll(List.of("副詞","連体詞","接続詞","感動詞","接頭語","助数詞","記号","顔文字"));return List.copyOf(result);
    }
    static Rule rule(String partOfSpeech){return RULES.get(partOfSpeech);}
    private static void put(String pos,String reading,String word,List<Form> forms){RULES.put(pos,new Rule(reading,word,List.copyOf(forms)));}
    private static void same(List<Form> forms,String... suffixes){for(String suffix:suffixes)forms.add(new Form(suffix,suffix));}
    private static void ichidan(List<Form> forms,String stem){
        for(String suffix:List.of("る","","ない","なかった","なくて","なく","なければ","ます","ました","ません","ませんでした","たい","たかった","たくない","て","た","たら","たり","ている","ていた","ています","ていない","れば","ろ","よ","よう"))same(forms,stem+suffix);
        // Passive/potential and causative forms are themselves ichidan verbs.
        if(stem.isEmpty())for(String derived:List.of("られ","させ","させられ"))ichidan(forms,derived);
    }
    private static void godan(String row,String base,String a,String i,String e,String o,String te,String ta){
        List<Form> forms=new ArrayList<>();
        same(forms,base,a+"ない",a+"なかった",a+"なくて",a+"なく",a+"なければ",i,i+"ます",i+"ました",i+"ません",i+"ませんでした",i+"たい",i+"たかった",i+"たくない",te,ta,ta+"ら",ta+"り",te+"いる",te+"いた",te+"います",te+"いない",e,e+"ば",o+"う");
        ichidan(forms,e);ichidan(forms,a+"れ");ichidan(forms,a+"せ");ichidan(forms,a+"せられ");
        put("動詞"+row+"行五段",base,base,forms);
    }
}
