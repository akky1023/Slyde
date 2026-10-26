package net.neomenia.glideprototype;

import android.content.SharedPreferences;
import android.text.InputType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class InputModeMemoryTest {
    private SharedPreferences preferences;
    private InputModeMemory memory;
    @Before public void setup(){
        preferences=InputPreferences.get(RuntimeEnvironment.getApplication());
        preferences.edit().clear().apply();memory=new InputModeMemory(preferences);
    }
    private void enable(boolean reset){preferences.edit().putBoolean("rememberInputMode",true).putBoolean("resetInputModeAfterThreeMinutes",reset).apply();}
    @Test public void defaultsKeepJapaneseAndFieldOverrides(){
        memory.changed(false);assertFalse(memory.startLatin(InputType.TYPE_CLASS_TEXT,1000));
        assertTrue(memory.startLatin(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,1000));
    }
    @Test public void explicitChoiceSurvivesRecreationAndLongAbsenceWithoutReset(){
        enable(false);memory.changed(false);memory.hidden(1000);
        assertTrue(new InputModeMemory(preferences).startLatin(InputType.TYPE_CLASS_TEXT,1000000));
        memory.changed(true);assertFalse(memory.startLatin(InputType.TYPE_CLASS_TEXT,1000001));
    }
    @Test public void resetOccursAtThreeMinutesAndStaysJapanese(){
        enable(true);memory.changed(false);memory.hidden(1000);
        assertTrue(memory.startLatin(InputType.TYPE_CLASS_TEXT,180999));
        memory.hidden(200000);
        assertFalse(new InputModeMemory(preferences).startLatin(InputType.TYPE_CLASS_TEXT,380000));
        assertFalse(memory.startLatin(InputType.TYPE_CLASS_TEXT,380001));
    }
    @Test public void activeEditorRestartDoesNotExpire(){
        enable(true);memory.changed(false);memory.hidden(1000);
        assertTrue(memory.startLatin(InputType.TYPE_CLASS_TEXT,2000));
        assertTrue(memory.startLatin(InputType.TYPE_CLASS_TEXT,1000000));
    }
    @Test public void forcedFieldsDoNotReplaceExplicitChoice(){
        enable(false);memory.changed(true);
        assertTrue(memory.startLatin(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,1000));
        assertFalse(memory.startLatin(InputType.TYPE_CLASS_TEXT,2000));
        memory.changed(false);memory.startLatin(InputType.TYPE_CLASS_NUMBER,3000);
        assertTrue(memory.startLatin(InputType.TYPE_CLASS_TEXT,4000));
    }
    @Test public void expiredMemoryStillHonorsPasswordField(){
        enable(true);memory.changed(false);memory.hidden(1000);
        assertTrue(memory.startLatin(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,181000));
        assertFalse(memory.startLatin(InputType.TYPE_CLASS_TEXT,181001));
    }
    @Test public void backwardsClockExpiresSafely(){
        enable(true);memory.changed(false);memory.hidden(2000);
        assertFalse(memory.startLatin(InputType.TYPE_CLASS_TEXT,1000));
    }
}
