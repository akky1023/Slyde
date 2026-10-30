package net.neomenia.glideprototype;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.neomenia.inputcore.UserDictionary;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class UserDictionaryStoreTest {
    @Test public void utf16AndUtf8BomImportsPreserveWordsAndComments()throws Exception {
        String text="すらいど\tSlyde\t固有名詞\tメモ\r\n";
        for(byte[] bytes:List.of(text.getBytes(StandardCharsets.UTF_16),("\ufeff"+text).getBytes(StandardCharsets.UTF_8))){
            UserDictionary dictionary=UserDictionaryStore.readImport(new ByteArrayInputStream(bytes));
            assertEquals(List.of("Slyde"),dictionary.exact("すらいど"));assertEquals("メモ",dictionary.entries().get(0).comment());
        }
    }
    @Test public void invalidImportLeavesSavedDictionaryIntact()throws Exception {
        UserDictionaryStore store=UserDictionaryStore.get(RuntimeEnvironment.getApplication());
        UserDictionary original=UserDictionary.read(new StringReader("すらいど\tSlyde\t名詞\n"));store.save(original);
        try{store.save(store.load().merge(UserDictionaryStore.readImport(new ByteArrayInputStream("不正".getBytes(StandardCharsets.UTF_8)))));fail();}catch(IOException expected){}
        assertSame(original,store.load());
        File file=new File(RuntimeEnvironment.getApplication().getFilesDir(),"user-dictionary.tsv");
        try(Reader reader=new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8)){
            assertEquals(original.entries(),UserDictionary.read(reader).entries());
        }
        UserDictionary changed=UserDictionary.read(new StringReader("すらいど\t更新\t名詞\n"));store.save(changed);
        assertSame(changed,store.peek());assertEquals(List.of("更新"),store.load().exact("すらいど"));
    }
    @Test public void invalidUtf8IsRejected()throws Exception {
        try{UserDictionaryStore.readImport(new ByteArrayInputStream(new byte[]{(byte)0xff}));fail();}catch(IOException expected){}
    }
}
