package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.stream.Collectors;

public class SymbolCatalogTest {
    private static void assertCategory(List<SymbolCatalog.Category> categories,String expected,String value){
        assertEquals(value,List.of(expected),categories.stream().filter(category->category.entries().stream().anyMatch(item->item.originalValue().equals(value))).map(SymbolCatalog.Category::name).toList());
    }
    @Test public void categoriesAreDisjointWithoutLosingExistingSymbols() throws Exception {
        var categories=SymbolCatalog.load();Map<String,String> owners=new HashMap<>();
        for(var category:categories)for(var item:category.entries())assertNull(item.value()+" in "+category.name(),owners.putIfAbsent(item.value(),category.name()));
        assertEquals(6994,owners.size());
        for(String removed:List.of("よく使う記号","半角記号","全角記号・句読点","文字様記号"))assertFalse(removed,categories.stream().anyMatch(category->category.name().equals(removed)));
        assertCategory(categories,"括弧","(");assertCategory(categories,"括弧","（");
        assertCategory(categories,"数学","+");assertCategory(categories,"通貨","$");
        assertCategory(categories,"単位","%");assertCategory(categories,"単位","℃");
        assertCategory(categories,"囲み英数字","⓫");assertCategory(categories,"囲み英数字","⓵");
    }
    @Test public void enclosedHangulAndComposedCharactersHaveSeparateOwners() throws Exception {
        var categories=SymbolCatalog.load();
        for(int cp=0x3200;cp<=0x321e;cp++)assertCategory(categories,"囲みハングル",new String(Character.toChars(cp)));
        for(int cp=0x3260;cp<=0x327e;cp++)assertCategory(categories,"囲みハングル",new String(Character.toChars(cp)));
        for(String value:List.of("㈱","㊀","㊗","㋐"))assertCategory(categories,"囲み日本語",value);
        for(String value:List.of("㍿","㋿","㍻","㎏","㌔","㋀","㍘","㏠","⒈","㉐"))assertCategory(categories,"組み文字",value);
        for(String value:List.of("⸀","⸢","℈","⌬","㉿"))assertCategory(categories,"特殊記号",value);
    }
    @Test public void circledNumbersOneThroughFiftyStayTogetherInNumericOrder() throws Exception {
        var categories=SymbolCatalog.load();
        List<String> numbers="①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳㉑㉒㉓㉔㉕㉖㉗㉘㉙㉚㉛㉜㉝㉞㉟㊱㊲㊳㊴㊵㊶㊷㊸㊹㊺㊻㊼㊽㊾㊿".codePoints().mapToObj(cp->new String(Character.toChars(cp))).toList();
        assertEquals(50,numbers.size());
        var enclosed=categories.stream().filter(category->category.name().equals("囲み英数字")).findFirst().orElseThrow();
        assertEquals(numbers,enclosed.entries().stream().limit(50).map(SymbolCatalog.Entry::value).toList());
        for(String number:numbers)assertEquals(number,List.of("囲み英数字"),categories.stream().filter(category->category.entries().stream().anyMatch(item->item.value().equals(number))).map(SymbolCatalog.Category::name).toList());
    }
    @Test public void composedErasShareOneCategoryInNewestFirstOrder() throws Exception {
        var categories=SymbolCatalog.load();
        List<String> eras=List.of("㋿","㍻","㍼","㍽","㍾");
        var japanese=categories.stream().filter(category->category.name().equals("組み文字")).findFirst().orElseThrow();
        assertEquals(eras,japanese.entries().stream().map(SymbolCatalog.Entry::value).filter(eras::contains).toList());
        assertEquals(eras.subList(0,2),japanese.entries().stream().limit(2).map(SymbolCatalog.Entry::value).toList());
        for(String era:eras)assertCategory(categories,"組み文字",era);
    }
    @Test public void categoriesGroupCommonSymbolsBeforeSpecialistOnes() throws Exception {
        var categories=SymbolCatalog.load();
        Map<String,List<String>> values=categories.stream().collect(Collectors.toMap(SymbolCatalog.Category::name,c->c.entries().stream().map(SymbolCatalog.Entry::value).toList()));
        assertEquals(18,values.keySet().stream().filter(name->!name.startsWith("絵文字：")).count());
        assertEquals(List.of("「","」","（","）","(",")","【","】","『","』"),values.get("括弧").subList(0,10));
        assertEquals(List.of("→","←","↑","↓"),values.get("矢印").subList(0,4));
        assertEquals(List.of("=","+","−","×","÷","±"),values.get("数学").subList(0,6));
        assertEquals(List.of("─","│","┌","┐","└","┘"),values.get("罫線").subList(0,6));
        assertTrue(values.get("数学").indexOf("∑")<values.get("数学").indexOf("⨀"));
        for(var category:categories)assertEquals(category.name(),category.entries().size(),new HashSet<>(category.entries()).size());
    }
    @Test public void fixedFrequencySectionsKeepEveryItemAndMarkRareOnlyCategories() throws Exception {
        var categories=SymbolCatalog.load();
        for(var category:categories)assertTrue(category.name(),category.commonCount()>=0&&category.commonCount()<=category.entries().size());
        for(String name:List.of("特殊記号","囲みハングル","点字","絵文字：肌・髪"))assertEquals(name,0,categories.stream().filter(c->c.name().equals(name)).findFirst().orElseThrow().commonCount());
        var numbers=categories.stream().filter(c->c.name().equals("囲み英数字")).findFirst().orElseThrow();
        assertEquals(20,numbers.commonCount());assertEquals("⑳",numbers.entries().get(numbers.commonCount()-1).value());assertEquals("㉑",numbers.entries().get(numbers.commonCount()).value());
    }
    @Test public void includesCommonSymbolsAcrossCategories() throws Exception {
        var categories=SymbolCatalog.load();
        Set<String> symbols=categories.stream().flatMap(category->category.entries().stream()).map(SymbolCatalog.Entry::originalValue).collect(Collectors.toSet());
        for(String required:List.of("'","\"","\\","@","～","「","」","→","⇒","∑","≠","€","℃","①","㍑","┏","♥"))assertTrue(required,symbols.contains(required));
        for(var category:categories){
            assertFalse(category.name(),category.entries().isEmpty());
            for(var entry:category.entries()){
                assertFalse(entry.value().isEmpty());
                assertFalse(entry.name().isEmpty());
                assertFalse(entry.value().codePoints().anyMatch(Character::isISOControl));
            }
        }
    }
    @Test public void emojiKeepsJoinedFlagsSkinTonesAndVariationSelectors() throws Exception {
        var categories=SymbolCatalog.load();
        Set<String> emoji=categories.stream().filter(category->category.name().startsWith("絵文字：")).flatMap(category->category.entries().stream()).map(SymbolCatalog.Entry::value).collect(Collectors.toSet());
        assertEquals(3790,emoji.size()); // 3781 fully-qualified sequences and 9 standalone components.
        for(String required:List.of("😀","❤️","👍🏽","👨‍👩‍👧‍👦","🇯🇵","1️⃣","🏳️‍🌈","🫩"))assertTrue(required,emoji.contains(required));
        assertFalse(emoji.contains("❤"));
        assertFalse(emoji.contains("🇯"));
    }
}
