package net.neomenia.glideprototype;

import java.io.StringReader;
import java.util.*;
import net.neomenia.inputcore.UserDictionary;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class UserDictionaryConversionTest {
    @Test public void inflectedCustomCandidatesLeadAndRefreshAfterEditing()throws Exception {
        var store=UserDictionaryStore.get(RuntimeEnvironment.getApplication());
        try{
            store.save(UserDictionary.read(new StringReader("ぐぐる\t独自ググる\t動詞ラ行五段\n")));
            SumireAdapter adapter=new SumireAdapter(RuntimeEnvironment.getApplication());
            assertEquals("独自ググった",adapter.candidates("ぐぐった",()->false).get(0));
            assertEquals("独自ググりませんでした",adapter.candidates("ぐぐりませんでした",()->false).get(0));
            assertTrue(adapter.candidates("ぐぐったけっか",()->false).get(0).startsWith("独自ググった"));
            store.save(UserDictionary.read(new StringReader("ぐぐる\t変更ググる\t動詞ラ行五段\n")));
            assertNull(adapter.cachedCandidates("ぐぐった"));assertEquals("変更ググった",adapter.candidates("ぐぐった",()->false).get(0));
        }finally{store.save(new UserDictionary(List.of()));}
    }
    @Test public void customCandidatesLeadAndEditsInvalidateCachedReadings()throws Exception {
        var context=RuntimeEnvironment.getApplication();var store=UserDictionaryStore.get(context);
        store.save(UserDictionary.read(new StringReader("すらいど\tSlydeCustom\t名詞\n")));
        assertEquals(List.of("SlydeCustom"),store.load().exact("すらいど"));
        assertSame(store,UserDictionaryStore.get(context));
        SumireAdapter adapter=new SumireAdapter(context);
        List<String> first=adapter.candidates("すらいど",()->false);assertEquals("SlydeCustom",first.get(0));assertTrue(first.size()>1);assertEquals(first,adapter.cachedCandidates("すらいど"));
        store.save(UserDictionary.read(new StringReader("すらいど\tChangedCustom\t名詞\n")));
        assertNull(adapter.cachedCandidates("すらいど"));List<String> changed=adapter.candidates("すらいど",()->false);assertEquals("ChangedCustom",changed.get(0));assertFalse(changed.contains("SlydeCustom"));
        List<String> sentence=adapter.candidates("すらいどをつかう",()->false);assertTrue(sentence.get(0),sentence.get(0).startsWith("ChangedCustom"));assertTrue(sentence.get(0),sentence.get(0).endsWith("使う"));
        store.save(new UserDictionary(List.of()));assertNull(adapter.cachedCandidates("すらいど"));assertFalse(adapter.candidates("すらいど",()->false).contains("ChangedCustom"));
        store.save(UserDictionary.read(new StringReader("すらいど\tスライド\t抑制単語\nほし\t★\t抑制単語\n")));
        assertNull(adapter.cachedCandidates("すらいど"));assertFalse(adapter.candidates("すらいど",()->false).contains("スライド"));assertFalse(adapter.candidates("ほし",()->false).contains("★"));
        store.save(UserDictionary.read(new StringReader("すらいど\tSlydeCustom\t名詞\nすらいど\tSlydeCustom\t抑制\n")));
        List<String> demoted=adapter.candidates("すらいど",()->false);assertTrue(demoted.contains("SlydeCustom"));assertEquals("SlydeCustom",demoted.get(demoted.size()-1));
        store.save(new UserDictionary(List.of()));assertTrue(adapter.candidates("すらいど",()->false).contains("スライド"));
        store.save(UserDictionary.read(new StringReader("すらいど\t\t抑制単語\n")));assertTrue(adapter.candidates("すらいど",()->false).isEmpty());
    }
}
