package net.neomenia.glideprototype;

import android.content.SharedPreferences;
import net.neomenia.inputcore.EditorPolicy;

/** Remembers explicit letter-mode choices without saving field-specific overrides. */
final class InputModeMemory {
    static final long RESET_DELAY_MS=3*60*1000L;
    private final SharedPreferences preferences;
    InputModeMemory(SharedPreferences preferences){this.preferences=preferences;}

    boolean startLatin(int inputType,long now){
        long hiddenAt=preferences.getLong("inputModeHiddenAt",-1);
        boolean expired=preferences.getBoolean("resetInputModeAfterThreeMinutes",false)
                &&hiddenAt>=0&&(now<hiddenAt||now-hiddenAt>=RESET_DELAY_MS);
        boolean remember=preferences.getBoolean("rememberInputMode",false);
        boolean japanese=!remember||expired||preferences.getBoolean("savedJapaneseInputMode",true);
        SharedPreferences.Editor editor=preferences.edit().remove("inputModeHiddenAt");
        if(!remember||expired)editor.putBoolean("savedJapaneseInputMode",true);
        editor.apply();
        return EditorPolicy.prefersLatin(inputType)||!japanese;
    }
    void changed(boolean japanese){
        if(preferences.getBoolean("rememberInputMode",false))
            preferences.edit().putBoolean("savedJapaneseInputMode",japanese).apply();
    }
    void hidden(long now){preferences.edit().putLong("inputModeHiddenAt",now).apply();}
}
