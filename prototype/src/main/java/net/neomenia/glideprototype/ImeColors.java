package net.neomenia.glideprototype;

import android.content.Context;
import android.graphics.Color;

/** Shared palette for the candidate strip and the keyboard's long-press menu. */
record ImeColors(int surface,int key,int text,int secondary,int accent,int selection,int divider) {
    int neutralText(){return androidx.core.graphics.ColorUtils.calculateLuminance(surface)>.5?Color.BLACK:Color.WHITE;}
    static ImeColors from(Context context){
        if(InputPreferences.get(context).getBoolean("composeKeyboard",false)){
            context=ThemeMode.colors(context);
            return new ImeColors(
                context.getColor(R.color.settings_surface_container),context.getColor(R.color.settings_surface_container_highest),
                context.getColor(R.color.settings_on_surface),context.getColor(R.color.settings_on_surface_variant),
                context.getColor(R.color.settings_primary),context.getColor(R.color.settings_primary_container),
                context.getColor(R.color.settings_outline_variant));
        }
        return new ImeColors(Color.rgb(21,30,39),Color.rgb(34,47,59),Color.rgb(235,242,246),
            Color.rgb(160,191,207),Color.rgb(104,181,255),Color.rgb(69,104,118),Color.rgb(61,76,88));
    }
}
