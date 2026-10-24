package net.neomenia.inputcore;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SymbolPresentationTest {
    @Test public void addsOnlyRegisteredSelectorsToSingleBases() throws Exception {
        var presentation=SymbolPresentation.load();
        for(String value:List.of("☀","☎","♥","⌚","☕","©")){
            assertEquals(value+"\ufe0e",presentation.apply(value,false));
            assertEquals(value+"\ufe0f",presentation.apply(value,true));
        }
        for(String value:List.of("","あ","→","㋿","abc","😀")){
            assertEquals(value,presentation.apply(value,false));assertEquals(value,presentation.apply(value,true));
        }
    }
    @Test public void leavesExistingPresentationAndJoinedSequencesUntouched() throws Exception {
        var presentation=SymbolPresentation.load();
        for(String value:List.of("☀︎","☀️","❤️","🇯🇵","👨‍👩‍👧‍👦","🏳️‍🌈","👍🏽","1️⃣","🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F")){
            assertEquals(value,presentation.apply(value,false));assertEquals(value,presentation.apply(value,true));
        }
    }
    @Test public void catalogSendsDistinctTextAndEmojiValuesWithoutChangingSections() throws Exception {
        var categories=SymbolCatalog.load();
        var text=categories.stream().filter(c->!c.name().startsWith("絵文字：")).flatMap(c->c.entries().stream()).toList();
        var emoji=categories.stream().filter(c->c.name().startsWith("絵文字：")).flatMap(c->c.entries().stream()).toList();
        assertTrue(text.stream().anyMatch(e->e.originalValue().equals("☀")&&e.value().equals("☀\ufe0e")));
        assertTrue(emoji.stream().anyMatch(e->e.originalValue().equals("☀\ufe0f")&&e.value().equals("☀\ufe0f")));
        assertTrue(emoji.stream().anyMatch(e->e.originalValue().equals("⌚")&&e.value().equals("⌚\ufe0f")));
        for(var entry:text)assertFalse(entry.value(),entry.value().contains("\ufe0f"));
        assertEquals(3790,emoji.size());
    }
    @Test public void textSelectorsDoNotHideWidthAnnotations(){
        assertEquals(CharacterWidth.badge("#"),CharacterWidth.badge("#\ufe0e"));
        assertEquals("[半]",CharacterWidth.badges(List.of("#\ufe0e","＃")).get("#\ufe0e"));
    }
}
