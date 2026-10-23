package net.neomenia.glideprototype;

import android.content.Context;
import android.content.SharedPreferences;

/** Preserve the original preference file across the settings-only migration. */
final class InputPreferences {
    static SharedPreferences get(Context context){return context.getSharedPreferences("PracticeActivity",Context.MODE_PRIVATE);}
}
