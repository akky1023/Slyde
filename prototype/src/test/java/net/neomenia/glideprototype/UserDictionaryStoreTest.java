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
        assertEquals(original.entries(),fresh().load().entries());
        UserDictionary changed=UserDictionary.read(new StringReader("すらいど\t更新\t名詞\n"));store.save(changed);
        assertSame(changed,store.peek());assertEquals(List.of("更新"),store.load().exact("すらいど"));
    }
    @Test public void invalidUtf8IsRejected()throws Exception {
        try{UserDictionaryStore.readImport(new ByteArrayInputStream(new byte[]{(byte)0xff}));fail();}catch(IOException expected){}
    }
    private static UserDictionaryStore fresh(){return new UserDictionaryStore(new File(RuntimeEnvironment.getApplication().getFilesDir(),"user-dictionary.tsv"));}
    @Test public void legacyDictionaryMigratesWithoutLosingCommentsOrPolicies()throws Exception {
        File path=new File(RuntimeEnvironment.getApplication().getFilesDir(),"user-dictionary.tsv");
        String text="はし\t橋\t名詞\tメモ\nはし\t箸\t抑制単語\n";
        try(Writer writer=new OutputStreamWriter(new FileOutputStream(path),StandardCharsets.UTF_8)){writer.write(text);}
        var store=fresh();assertEquals("標準辞書",store.groups().get(0).name());assertEquals("メモ",store.load().entries().get(0).comment());
        store.create("仕事");var reloaded=fresh();assertEquals(2,reloaded.groups().size());assertEquals(List.of("橋"),reloaded.load().exact("はし"));assertTrue(reloaded.load().isExcluded("はし","箸"));
        assertTrue(path.exists());
    }
    @Test public void moveRenameAndDeletePersistAndRefreshCombinedSnapshot()throws Exception {
        var store=fresh();var entry=new UserDictionary.Entry("はし","橋","名詞","メモ");store.save(new UserDictionary(List.of(entry)));
        String source=store.groups().get(0).id(),target=store.create("仕事");UserDictionary before=store.peek();
        store.edit(source,target,entry,entry);assertNotSame(before,store.peek());assertTrue(store.group(source).dictionary().entries().isEmpty());
        assertEquals(List.of(entry),store.group(target).dictionary().entries());assertEquals(List.of("橋"),store.load().exact("はし"));
        store.rename(target,"専門用語");store=fresh();assertEquals("専門用語",store.group(target).name());assertEquals(List.of(entry),store.group(target).dictionary().entries());
        store.delete(target);assertTrue(store.peek().entries().isEmpty());assertEquals(1,fresh().groups().size());
        try{store.delete(source);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void duplicateNamesAndMissingDestinationsLeaveStorageIntact()throws Exception {
        var store=fresh();String target=store.create("仕事");var entry=new UserDictionary.Entry("はし","橋","名詞","");store.save(target,new UserDictionary(List.of(entry)));
        UserDictionary before=store.peek();
        for(String name:List.of("仕事"," ","a\nb")){try{store.create(name);fail();}catch(IllegalArgumentException expected){}}
        try{store.edit(target,"missing",entry,entry);fail();}catch(IllegalArgumentException expected){}
        assertSame(before,store.peek());assertEquals(List.of(entry),fresh().group(target).dictionary().entries());
    }
    @Test public void exclusionsApplyAcrossDictionariesAndDeletingOneKeepsOtherWords()throws Exception {
        var store=fresh();store.save(UserDictionary.read(new StringReader("はし\t橋\t名詞\n")));String target=store.create("除外設定");
        store.save(target,UserDictionary.read(new StringReader("はし\t橋\t抑制単語\n")));assertTrue(store.load().exact("はし").isEmpty());
        store.delete(target);assertEquals(List.of("橋"),store.load().exact("はし"));
    }
}
