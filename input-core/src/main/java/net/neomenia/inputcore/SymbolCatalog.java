package net.neomenia.inputcore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline symbol palette, including every fully-qualified Emoji 16.0 sequence. */
public final class SymbolCatalog {
    public record Entry(String value,String name) {}
    public record Category(String name,List<Entry> entries) {}

    public static List<Category> load() throws IOException {
        List<Category> categories=new ArrayList<>();
        addText(categories,"よく使う記号","、。，．・：；？！゛゜´｀¨＾￣＿ヽヾゝゞ〃仝々〆〇ー―‐／＼～〜‖｜…‥‘’“”（）〔〕［］｛｝〈〉《》「」『』【】〒※☆★○●◎◇◆□■△▲▽▼♪♬♯♭†‡§¶©®™✓✔✕✖♥♡");
        addRange(categories,"半角記号",new int[][]{{0x21,0x7e}});
        addRange(categories,"全角記号・括弧",new int[][]{{0xff01,0xff65},{0x3001,0x303f},{0x2000,0x206f},{0x2e00,0x2e7f}});
        addRange(categories,"矢印",new int[][]{{0x2190,0x21ff},{0x27f0,0x27ff},{0x2900,0x297f},{0x2b00,0x2b11}});
        addRange(categories,"数学",new int[][]{{0x2200,0x22ff},{0x27c0,0x27ef},{0x2980,0x2aff}});
        addText(categories,"通貨・単位","$¢£¤¥€₩₹₽₺₫฿₱₪₴₦₿￥％‰‱℃℉°′″ℓℏ№℡ÅΩµμ");
        addRange(categories,"単位・囲み文字",new int[][]{{0x2100,0x214f},{0x2150,0x218b},{0x2460,0x24ff},{0x3200,0x33ff}},true);
        addRange(categories,"図形・罫線",new int[][]{{0x2300,0x23ff},{0x2500,0x27bf},{0x2800,0x28ff}});
        Map<String,String> names=new LinkedHashMap<>();
        names.put("Smileys & Emotion","絵文字：顔・感情");names.put("People & Body","絵文字：人・手");
        names.put("Component","絵文字：肌・髪");names.put("Animals & Nature","絵文字：動物・自然");
        names.put("Food & Drink","絵文字：食べ物");names.put("Travel & Places","絵文字：乗り物・場所");
        names.put("Activities","絵文字：活動");names.put("Objects","絵文字：道具");
        names.put("Symbols","絵文字：記号");names.put("Flags","絵文字：旗");
        InputStream source=SymbolCatalog.class.getResourceAsStream("/symbols/emoji-test.txt");
        if(source==null)throw new IOException("絵文字データが見つかりません");
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(source,StandardCharsets.UTF_8))){
            List<Entry> entries=null;String line;
            while((line=reader.readLine())!=null){
                if(line.startsWith("# group: ")){
                    String group=line.substring(9).trim();entries=new ArrayList<>();
                    categories.add(new Category(names.getOrDefault(group,group),entries));
                }else if(entries!=null&&!line.startsWith("#")&&line.contains(";")){
                    String[] parts=line.split(";",2);String status=parts[1].split("#",2)[0].trim();
                    if(!status.equals("fully-qualified")&&!status.equals("component"))continue;
                    StringBuilder value=new StringBuilder();
                    for(String hex:parts[0].trim().split("\\s+"))value.appendCodePoint(Integer.parseInt(hex,16));
                    String description=parts[1].substring(parts[1].indexOf('#')+1).trim();
                    entries.add(new Entry(value.toString(),description));
                }
            }
        }
        return categories;
    }
    private static void addText(List<Category> out,String name,String values){
        List<Entry> entries=new ArrayList<>();values.codePoints().distinct().forEach(cp->entries.add(entry(cp)));
        out.add(new Category(name,entries));
    }
    private static Entry entry(int cp){return new Entry(new String(Character.toChars(cp)),String.format(Locale.ROOT,"U+%04X",cp));}
    private static void addRange(List<Category> out,String name,int[][] ranges){addRange(out,name,ranges,false);}
    private static void addRange(List<Category> out,String name,int[][] ranges,boolean includeLetters){
        List<Entry> entries=new ArrayList<>();
        for(int[] range:ranges)for(int cp=range[0];cp<=range[1];cp++){
            int type=Character.getType(cp);
            if((type>=Character.DASH_PUNCTUATION&&type<=Character.OTHER_SYMBOL)||type==Character.INITIAL_QUOTE_PUNCTUATION||type==Character.FINAL_QUOTE_PUNCTUATION||
                (includeLetters&&Character.isDefined(cp)&&!Character.isWhitespace(cp)))entries.add(entry(cp));
        }
        out.add(new Category(name,entries));
    }
}
