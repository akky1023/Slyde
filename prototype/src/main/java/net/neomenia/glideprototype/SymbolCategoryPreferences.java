package net.neomenia.glideprototype;

import android.content.SharedPreferences;

/** Category names are persisted independently of their order in the catalog. */
final class SymbolCategoryPreferences {
    static final String HISTORY="履歴";
    static String key(String name){return "symbolCategory.enabled."+name;}
    static boolean enabled(SharedPreferences preferences,String name){return preferences.getBoolean(key(name),true);}
}
