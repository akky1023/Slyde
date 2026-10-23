package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.stream.Collectors;

public class SymbolCatalogTest {
    @Test public void includesCommonSymbolsAcrossCategories() throws Exception {
        var categories=SymbolCatalog.load();
        Set<String> symbols=categories.stream().flatMap(category->category.entries().stream()).map(SymbolCatalog.Entry::value).collect(Collectors.toSet());
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
