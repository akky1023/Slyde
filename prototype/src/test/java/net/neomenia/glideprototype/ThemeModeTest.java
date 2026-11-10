package net.neomenia.glideprototype;

import android.content.Context;
import android.content.res.Configuration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ThemeModeTest {
    @Test @Config(qualifiers="notnight") public void imeCanUseDarkColorsWhileDeviceStaysLight(){verifyOverride(ThemeMode.DARK,Configuration.UI_MODE_NIGHT_YES,Configuration.UI_MODE_NIGHT_NO);}
    @Test @Config(qualifiers="night") public void imeCanUseLightColorsWhileDeviceStaysDark(){verifyOverride(ThemeMode.LIGHT,Configuration.UI_MODE_NIGHT_NO,Configuration.UI_MODE_NIGHT_YES);}
    private void verifyOverride(int mode,int selectedNight,int systemNight){
        Context context=RuntimeEnvironment.getApplication();
        InputPreferences.get(context).edit().putInt(ThemeMode.KEY,mode).putBoolean("composeKeyboard",true).apply();
        Context palette=ThemeMode.colors(context);
        assertEquals(selectedNight,palette.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK);
        assertEquals(palette.getColor(R.color.settings_surface_container),ImeColors.from(context).surface());
        assertEquals(palette.getColor(R.color.settings_on_surface),ImeColors.from(context).text());
        assertEquals(systemNight,context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK);
        InputPreferences.get(context).edit().putInt(ThemeMode.KEY,ThemeMode.SYSTEM).apply();
        assertEquals(systemNight,ThemeMode.colors(context).getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK);
    }
}
