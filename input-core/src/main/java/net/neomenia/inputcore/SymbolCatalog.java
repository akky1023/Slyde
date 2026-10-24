package net.neomenia.inputcore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline symbol palette, including every fully-qualified Emoji 16.0 sequence. */
public final class SymbolCatalog {
    public record Entry(String value,String name,String originalValue) {
        public Entry(String value,String name){this(value,name,value);}
    }
    public record Category(String name,List<Entry> entries,int commonCount) {
        public Category(String name,List<Entry> entries){this(name,entries,entries.size());}
    }
    private static final String DOCUMENT="、。，．・：；？！゛゜´｀¨￣＿ヽヾゝゞ〃仝々〆〇ー―‐／＼～〜‖…‥‘’“”〒※†‡§¶©®™℠@#&_\\/.,!?:;\"'`~";
    private static final String UNITS="%％‰‱℃℉°′″ℓÅΩµμ";
    private static final String TECHNICAL="⌘⌥⌃⌅⌫⌦⌧⎋⏎⌨⏏⏻⏼⏽⏾⏯⏮⏭⏸⏹⏺⌚⌛⏰⏱⏲⏳⌂⌕⌖⌒";
    private static final String DECORATION="☆★♡♥♧♣♢♦♤♠♪♫♬♩♭♮♯✓✔✕✖✗✘☐☑☒☀☁☂☃☄☎☏☕♨⚠⚡⚪⚫✉✂✏✒✦✧✩✪✿❀❁❃❋";
    private static final List<String> TEXT_CATEGORIES=List.of("句読点・文書","括弧","数学","矢印","通貨","単位","分数・ローマ数字","囲み英数字","囲み日本語","囲みハングル","組み文字","技術記号","罫線","ブロック","図形","装飾・音楽","点字","特殊記号");

    public static List<Category> load() throws IOException {
        List<Category> categories=new ArrayList<>();
        // Gather the repertoire first. Category ownership is assigned once, by meaning.
        Set<Entry> symbols=new LinkedHashSet<>();
        addText(symbols,"、。，．・：；？！゛゜´｀¨＾￣＿ヽヾゝゞ〃仝々〆〇ー―‐／＼～〜‖｜…‥‘’“”（）〔〕［］｛｝〈〉《》「」『』【】〒※☆★○●◎◇◆□■△▲▽▼♪♬♯♭†‡§¶©®™✓✔✕✖♥♡"+
            "+−±×÷=<>＜＞$¢£¤¥€₩₹₽₺₫฿₱₪₴₦₿￥%％‰‱℃℉°′″ℓℏ№℡ÅΩµμ");
        addRange(symbols,new int[][]{{0x21,0x7e},{0xff01,0xff65},{0x3001,0x303f},{0x2000,0x206f},{0x2e00,0x2e7f},
            {0x2190,0x21ff},{0x27f0,0x27ff},{0x2900,0x297f},{0x2b00,0x2b11},{0x2200,0x22ff},{0x27c0,0x27ef},{0x2980,0x2aff},
            {0x2300,0x23ff},{0x2500,0x27bf},{0x2800,0x28ff}},false);
        addRange(symbols,new int[][]{{0x2100,0x214f},{0x2150,0x218b},{0x2460,0x24ff},{0x3200,0x33ff}},true);
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
        Set<String> emojiValues=new HashSet<>();for(Category category:categories)for(Entry item:category.entries())emojiValues.add(item.value());
        Map<String,List<Entry>> groups=new LinkedHashMap<>();for(String name:TEXT_CATEGORIES)groups.put(name,new ArrayList<>());
        Set<String> assigned=new HashSet<>(emojiValues);
        for(Entry item:symbols)if(assigned.add(item.value()))groups.get(categoryFor(item.value().codePointAt(0))).add(item);
        List<Category> result=new ArrayList<>();for(var group:groups.entrySet())result.add(new Category(group.getKey(),group.getValue()));
        result.addAll(categories);
        SymbolPresentation presentation=SymbolPresentation.load();List<Category> styled=new ArrayList<>();
        for(Category category:prioritize(result)){
            boolean emoji=category.name().startsWith("絵文字：");List<Entry> entries=new ArrayList<>();
            for(Entry item:category.entries())entries.add(new Entry(presentation.apply(item.value(),emoji),item.name(),item.originalValue()));
            styled.add(new Category(category.name(),entries,category.commonCount()));
        }
        return styled;
    }
    private static boolean within(int cp,int first,int last){return cp>=first&&cp<=last;}
    private static boolean contains(String values,int cp){return values.indexOf(cp)>=0;}
    private static String categoryFor(int cp){
        if(within(cp,0x3200,0x321e)||within(cp,0x3260,0x327e))return "囲みハングル";
        if(within(cp,0x3220,0x3247)||within(cp,0x3280,0x32b0)||within(cp,0x32d0,0x32fe))return "囲み日本語";
        if(within(cp,0x2460,0x2487)||within(cp,0x249c,0x24ff)||within(cp,0x3248,0x324f)||within(cp,0x3251,0x325f)||within(cp,0x32b1,0x32bf)||within(cp,0x2776,0x2793))return "囲み英数字";
        if(within(cp,0x2488,0x249b)||cp==0x3250||within(cp,0x32c0,0x32cf)||within(cp,0x32ff,0x33ff)||contains("℀℁℅℆№℡℻",cp))return "組み文字";
        if(within(cp,0x2e00,0x2e7f))return "特殊記号";
        int type=Character.getType(cp),ascii=within(cp,0xff01,0xff5e)?cp-0xfee0:cp;
        if(type==Character.START_PUNCTUATION||type==Character.END_PUNCTUATION||contains("<>＜＞",cp))return "括弧";
        if(within(cp,0x2190,0x21ff)||within(cp,0x27f0,0x27ff)||within(cp,0x2900,0x297f)||within(cp,0x2b00,0x2b11)||cp==0x2794||within(cp,0x2798,0x27be))return "矢印";
        if(type==Character.CURRENCY_SYMBOL)return "通貨";
        if(contains(UNITS,cp))return "単位";
        if(within(cp,0x2150,0x217f))return "分数・ローマ数字";
        if(type==Character.MATH_SYMBOL||within(cp,0x2200,0x22ff)||within(cp,0x27c0,0x27ef)||within(cp,0x2980,0x2aff)||contains("+-=*^|ℂℍℕℙℚℝℤℬℰℱℋℐℒℳℛℜℑℵℶℷℸℏ",ascii))return "数学";
        if(contains(DOCUMENT,cp)||contains(DOCUMENT,ascii))return "句読点・文書";
        if(within(cp,0x2500,0x257f))return "罫線";
        if(within(cp,0x2580,0x259f))return "ブロック";
        if(within(cp,0x25a0,0x25ff))return "図形";
        if(contains(TECHNICAL,cp))return "技術記号";
        if(contains(DECORATION,cp)||within(cp,0x2654,0x265f))return "装飾・音楽";
        if(within(cp,0x2800,0x28ff))return "点字";
        return "特殊記号";
    }
    /** Common symbols are grouped by use; the remaining specialist symbols stay at the end. */
    private static List<Category> prioritize(List<Category> categories){
        Map<String,String> first=Map.ofEntries(
            Map.entry("句読点・文書","、。！？・：；…‥ー―‐～〜／＼‖‘’“”\"'`.,!?:;/\\@#&_~〒※©®™℠§¶†‡〃々〆〇゛゜´｀¨￣＿ヽヾゝゞ"),
            Map.entry("括弧","「」（）()【】『』[]{}［］｛｝〈〉《》〔〕<>«»‹›｢｣"),
            Map.entry("矢印","→←↑↓⇒⇐↔⇔↕⇑⇓↩↪↖↗↘↙⇕↰↱↲↳↶↷↺↻⟵⟶⟷⤴⤵"),
            Map.entry("数学","=+−×÷±≠≒≈≤≥∞√∑∫∈∉∪∩⊂⊃≡＜＞≦≧∏∬∭∮∂∆∇∋⊆⊇∅∀∃¬∧∨⊕⊗∴∵∠⊥∥∝"),
            Map.entry("通貨","¥￥$€₩£¢₽₹₿¤₱฿₫₴₺₪₦"),
            Map.entry("単位","%％℃°ℓµμ‰℉′″ΩÅ‱"),
            Map.entry("組み文字","㋿㍻㍿㎏㎝㎜㎡㎥㎞㌔㍍㌢㍉㌘㍑㌧㌫㏄№℡㍼㍽㍾㎎㎢㎖㎗㎘㎐㎑㎒㎓㎳㎽㎾㎷㎸㎃㎄℻℀℁℅℆"),
            Map.entry("分数・ローマ数字","½⅓⅔¼¾⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩⅪⅫⅰⅱⅲⅳⅴⅵⅶⅷⅸⅹⅺⅻ"),
            Map.entry("囲み英数字","①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳㉑㉒㉓㉔㉕㉖㉗㉘㉙㉚㉛㉜㉝㉞㉟㊱㊲㊳㊴㊵㊶㊷㊸㊹㊺㊻㊼㊽㊾㊿⓪⓿⓫⓬⓭⓮⓯⓰⓱⓲⓳⓴⓵⓶⓷⓸⓹⓺⓻⓼⓽⓾ⒶⒷⒸⒹⒺⒻⒼⒽⒾⒿⓀⓁⓂⓃⓄⓅⓆⓇⓈⓉⓊⓋⓌⓍⓎⓏⓐⓑⓒⓓⓔⓕⓖⓗⓘⓙⓚⓛⓜⓝⓞⓟⓠⓡⓢⓣⓤⓥⓦⓧⓨⓩ"),
            Map.entry("囲み日本語","㊀㊁㊂㊃㊄㊅㊆㊇㊈㊉㊊㊋㊌㊍㊎㊏㊐㊗㊡㊙㊥㊤㊦㊧㊨㊮㊑㊒㊓㊔㊕㊖㊰"),
            Map.entry("技術記号","⌘⌥⌃⌅⌫⌦⌧⎋⏎⌨⏏⏻⏼⏽⏾⏯⏮⏭⏸⏹⏺⌚⌛⏰⏱⏲⏳⌂⌕⌖⌒"),
            Map.entry("罫線","─│┌┐└┘├┤┬┴┼━┃┏┓┗┛┣┫┳┻╋═║╔╗╚╝╠╣╦╩╬┄┆┅┇╭╮╰╯╱╲╳"),
            Map.entry("ブロック","█▌▐▀▄░▒▓▉▊▋▍▎▏▁▂▃▅▆▇▖▗▘▝▙▟▛▜"),
            Map.entry("図形","○●◎◯◉◌◐◑□■▢▣▤▥▦▧▨▩△▲▽▼▷▶◁◀◇◆◊◈◠◡"),
            Map.entry("装飾・音楽","☆★♡♥♧♣♢♦♤♠♪♫♬♩♭♮♯✓✔✕✖✗✘☐☑☒☀☁☂☃☄☎☏☕♨⚠⚡⚪⚫✉✂✏✒✦✧✩✪✿❀❁❃❋†‡"),
            Map.entry("点字","⠀⠁⠃⠉⠙⠑⠋⠛⠓⠊⠚⠅⠇⠍⠝⠕⠏⠟⠗⠎⠞⠥⠧⠺⠭⠽⠵⠼⠿"),
            Map.entry("絵文字：顔・感情","❤️ 😂 🤣 😭 😊 🥰 😍 😘 😅 😆 😁 😀 😄 🙂 😉 🥺 🤔 😎 😋 🥲 😢 😔 😞 😣 😫 😱 😡 🙃 😌 😴 💕 💖 💔 💯 🧡 💛 💚 💙 💜 🖤 🤍 😃 🤬 🤤 🤮 🤧 💢 💤"),
            Map.entry("絵文字：人・手","👍 👎 👏 🙌 🙏 👌 ✌️ 🤞 🤝 💪 👋 🖐️ ✋ 🫶 👈 👉 👆 👇 ☝️ 🙆 🙅 🙋 🤷 🤦 🙇 👶 🧒 👦 👧 👨 👩 🧑 👴 👵 👫 👬 👭 🧑‍🤝‍🧑"),
            Map.entry("絵文字：動物・自然","🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🐟 🐬 🐳 🦋 🐝 🌸 🌹 🌻 🌷 🌼 🍀 🌱 🌿 🌳 🍁 🍂 🌈 🌙 ⭐ 🌟 ☀️ ☁️ ☔ ❄️ 🔥 💧"),
            Map.entry("絵文字：食べ物","🍚 🍙 🍱 🍣 🍜 🍛 🍝 🍞 🥐 🥪 🍔 🍟 🍕 🌭 🥗 🍖 🍗 🥩 🥚 🍳 🍎 🍊 🍋 🍌 🍉 🍇 🍓 🍑 🍒 🍅 🥕 🍰 🎂 🍩 🍪 🍫 🍬 🍡 🍦 ☕ 🍵 🥤 🧃 🍺 🍻 🍷 🥂"),
            Map.entry("絵文字：乗り物・場所","🚗 🚙 🚕 🚌 🚃 🚄 🚅 🚆 🚇 🚲 🏍️ ✈️ 🚀 🚢 ⛵ 🚁 🏠 🏡 🏢 🏫 🏥 🏪 🏨 🏦 🏣 ⛩️ 🗼 🏯 🏰 🏖️ ⛰️ 🗻 🏕️ 🕐 🕑 🕒 🕓 🕔 🕕 🕖 🕗 🕘 🕙 🕚 🕛"),
            Map.entry("絵文字：活動","🎉 🎊 🎈 🎁 🎀 🎄 🎃 🎆 🎇 ✨ 🎋 🎍 🎎 🎏 🎐 🎑 ⚽ ⚾ 🏀 🏐 🎾 🏓 🏸 🏈 ⛳ 🎣 🎿 🎮 🎲 🧩 🧸 🎨 🎭 🏆 🥇 🥈 🥉"),
            Map.entry("絵文字：道具","📱 💻 ⌨️ 🖥️ 🖨️ 📷 📸 📹 📺 📞 ☎️ 🎧 🎤 🎵 🎶 📚 📖 📝 ✏️ 🖊️ 📌 📎 📅 📆 📁 📂 📄 ✉️ 📩 📦 💰 💴 💵 💳 💡 🔦 🔑 🔒 🔓 🔔 ⏰ ⌚ 💊 💉 🔧 🔨 ✂️"),
            Map.entry("絵文字：記号","✅ ☑️ ✔️ ❌ ❎ ⭕ ❓ ❔ ❗ ❕ ⁉️ ‼️ ⚠️ 🚫 ⛔ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟤 🟥 🟧 🟨 🟩 🟦 🟪 ⬛ ⬜ 🟫 ➡️ ⬅️ ⬆️ ⬇️ ↔️ ↕️ 🔄 ➕ ➖ ✖️ ➗ 🟰 ♾️ 💲 💱"),
            Map.entry("絵文字：旗","🇯🇵 🇺🇸 🇰🇷 🇹🇼 🇨🇳 🇬🇧 🇩🇪 🇫🇷 🇮🇹 🇪🇸 🇨🇦 🇦🇺 🇮🇳 🇧🇷 🇹🇭 🇻🇳 🇮🇩 🇸🇬 🇺🇳 🏳️ 🏴 🏁 🚩 🎌 🏳️‍🌈 🏳️‍⚧️ 🏴‍☠️")
        );
        // Fixed Japanese-input usage estimates, not measured personal usage.
        Map<String,Integer> commonLimits=Map.ofEntries(
            Map.entry("句読点・文書",36),Map.entry("括弧",14),Map.entry("矢印",12),Map.entry("数学",24),
            Map.entry("通貨",6),Map.entry("単位",8),Map.entry("組み文字",20),Map.entry("分数・ローマ数字",24),
            Map.entry("囲み英数字",20),Map.entry("囲み日本語",32),Map.entry("囲みハングル",0),
            Map.entry("罫線",22),Map.entry("ブロック",8),Map.entry("図形",24),Map.entry("装飾・音楽",24),
            Map.entry("点字",0),Map.entry("特殊記号",0),Map.entry("絵文字：肌・髪",0),
            Map.entry("絵文字：旗",6),Map.entry("絵文字：乗り物・場所",32)
        );
        List<Category> ordered=new ArrayList<>();
        for(Category category:categories){
            Map<String,Entry> remaining=new LinkedHashMap<>();for(Entry item:category.entries())remaining.put(item.value(),item);
            List<Entry> entries=new ArrayList<>();
            String preferred=first.getOrDefault(category.name(),"");
            List<String> values=category.name().startsWith("絵文字：")?Arrays.asList(preferred.split(" ")):preferred.codePoints().mapToObj(cp->new String(Character.toChars(cp))).toList();
            for(String value:values){Entry item=remaining.remove(value);if(item!=null)entries.add(item);}
            int common=Math.min(entries.size(),commonLimits.getOrDefault(category.name(),entries.size()));
            entries.addAll(remaining.values());ordered.add(new Category(category.name(),entries,common));
        }
        return ordered;
    }
    private static void addText(Set<Entry> out,String values){
        values.codePoints().forEach(cp->out.add(entry(cp)));
    }
    private static Entry entry(int cp){return new Entry(new String(Character.toChars(cp)),String.format(Locale.ROOT,"U+%04X",cp));}
    private static void addRange(Set<Entry> out,int[][] ranges,boolean includeLetters){
        for(int[] range:ranges)for(int cp=range[0];cp<=range[1];cp++){
            int type=Character.getType(cp);
            if((type>=Character.DASH_PUNCTUATION&&type<=Character.OTHER_SYMBOL)||type==Character.INITIAL_QUOTE_PUNCTUATION||type==Character.FINAL_QUOTE_PUNCTUATION||
                (includeLetters&&Character.isDefined(cp)&&!Character.isWhitespace(cp)))out.add(entry(cp));
        }
    }
}
