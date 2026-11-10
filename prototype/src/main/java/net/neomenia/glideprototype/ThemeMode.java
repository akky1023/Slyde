package net.neomenia.glideprototype;

import android.content.Context;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

/** Resolve the saved theme for both Activity windows and the separate IME context. */
final class ThemeMode {
    static final String KEY="themeMode";
    static final int SYSTEM=0,LIGHT=1,DARK=2;

    static int get(Context context){
        int value=InputPreferences.get(context).getInt(KEY,SYSTEM);
        return value==LIGHT||value==DARK?value:SYSTEM;
    }
    static int nightMode(Context context){
        return switch(get(context)){
            case LIGHT->AppCompatDelegate.MODE_NIGHT_NO;
            case DARK->AppCompatDelegate.MODE_NIGHT_YES;
            default->AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        };
    }
    static void apply(AppCompatActivity activity){
        activity.getDelegate().setLocalNightMode(nightMode(activity));
    }
    static Context colors(Context context){
        int night=switch(get(context)){
            case LIGHT->Configuration.UI_MODE_NIGHT_NO;
            case DARK->Configuration.UI_MODE_NIGHT_YES;
            default->context.getApplicationContext().getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK;
        };
        Configuration configuration=context.getResources().getConfiguration();
        if((configuration.uiMode&Configuration.UI_MODE_NIGHT_MASK)==night)return context;
        Configuration overridden=new Configuration(configuration);
        overridden.uiMode=(overridden.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|night;
        return context.createConfigurationContext(overridden);
    }
}
