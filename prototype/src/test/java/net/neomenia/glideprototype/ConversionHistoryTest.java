package net.neomenia.glideprototype;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ConversionHistoryTest {
    private static EditorInfo editor(String app,int field){
        EditorInfo info=new EditorInfo();info.packageName=app;info.fieldId=field;
        info.inputType=InputType.TYPE_CLASS_TEXT;return info;
    }
    private static ConversionHistory history(){
        ConversionHistory history=new ConversionHistory();history.start(editor("chat",1));return history;
    }
    @Test public void retainsLastThreeChoicesAfterEditorIsEmptied(){
        ConversionHistory history=history();
        for(String value:new String[]{"古い話。","病院で","診察を","受けた。"})history.record(value);
        assertEquals("病院で診察を受けた。",history.context(""));
    }
    @Test public void sameEditorCanRestartAfterSending(){
        ConversionHistory history=history();history.record("診察を受けた。");
        history.start(editor("chat",1));assertEquals("診察を受けた。",history.context(""));
        history.record("医師に相談した。");
        assertEquals("診察を受けた。医師に相談した。",history.context("医師に相談した。"));
    }
    @Test public void visibleTextDoesNotDuplicateHistory(){
        ConversionHistory history=history();history.record("病院で");history.record("診察を");
        assertEquals("今日は病院で診察を",history.context("今日は病院で診察を"));
        assertEquals("今日は病院で診察を受けた。",history.context("今日は病院で診察を受けた。"));
        assertEquals("病院で診察を受けた。",history.context("で診察を受けた。"));
    }
    @Test public void newVisibleTextFollowsSentHistory(){
        ConversionHistory history=history();history.record("病院へ行った。");
        assertEquals("病院へ行った。\nその後、",history.context("その後、"));
    }
    @Test public void switchingAppOrFieldClearsHistory(){
        ConversionHistory history=history();history.record("前の欄");history.start(editor("chat",2));
        assertEquals("",history.context(""));
        history.record("別の欄");history.start(editor("mail",2));assertEquals("",history.context(""));
    }
    @Test public void privateEditorsNeitherRetainNorRecordHistory(){
        for(boolean password:new boolean[]{true,false}){
            ConversionHistory history=history();history.record("過去の文章");
            EditorInfo info=editor("chat",1);
            if(password)info.inputType|=InputType.TYPE_TEXT_VARIATION_PASSWORD;
            else info.imeOptions|=EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
            history.start(info);history.record("秘密");assertEquals("",history.context(""));
            history.start(editor("chat",1));assertEquals("",history.context(""));
        }
    }
    @Test public void contextIsBoundedWithoutSplittingSupplementaryCharacters(){
        ConversionHistory history=history();history.record("あ".repeat(300)+"😀".repeat(10));
        assertEquals("あ".repeat(182)+"😀".repeat(10),history.context(""));
    }
    @Test public void uncommittedOrBlankTextDoesNotEvictChoices(){
        ConversionHistory history=history();history.record("確定済み");history.record("");history.record("\n");
        assertEquals("確定済み",history.context(""));history.clear();assertEquals("",history.context(""));
    }
}
