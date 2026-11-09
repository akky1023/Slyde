package net.neomenia.glideprototype;

import android.content.Context;
import android.graphics.Color;
import android.widget.*;

/** Settings replace both the candidate strip and the input keys inside the IME. */
final class ImeSettingsMenu extends LinearLayout {
    ImeSettingsMenu(Context context,Runnable back,Runnable adjust,Runnable settings){
        this(context,back,adjust,settings,()->{},()->{});
    }
    ImeSettingsMenu(Context context,Runnable back,Runnable adjust,Runnable settings,Runnable dictionary,Runnable floatingChanged){
        super(context);setOrientation(VERTICAL);setBackgroundColor(Color.rgb(21,30,39));
        setContentDescription("キーボード設定一覧");
        int size=Math.round(48*getResources().getDisplayMetrics().density);
        ImageButton returnButton=UiIcons.button(context,R.drawable.ic_back,"戻る",back);
        LinearLayout toolbar=new LinearLayout(context);toolbar.setOrientation(HORIZONTAL);addView(toolbar,new LayoutParams(-1,size));
        toolbar.addView(returnButton,new LayoutParams(size,size));toolbar.addView(new android.view.View(context),new LayoutParams(0,size,1));
        ImageButton floating=UiIcons.button(context,R.drawable.ic_floating_keyboard,"フローティングキーボード",()->{});
        Runnable refreshFloating=()->{
            boolean enabled=InputPreferences.get(context).getBoolean("floatingKeyboard",false);
            floating.setSelected(enabled);floating.setColorFilter(enabled?Color.rgb(104,181,255):Color.WHITE);
            androidx.core.view.ViewCompat.setStateDescription(floating,enabled?"ON":"OFF");
        };
        refreshFloating.run();
        floating.setOnClickListener(view->{
            boolean enabled=!InputPreferences.get(context).getBoolean("floatingKeyboard",false);
            InputPreferences.get(context).edit().putBoolean("floatingKeyboard",enabled).apply();refreshFloating.run();floatingChanged.run();
        });
        toolbar.addView(floating,new LayoutParams(size,size));
        ScrollView scroll=new ScrollView(context);addView(scroll,new LayoutParams(-1,0,1));
        LinearLayout items=new LinearLayout(context);items.setOrientation(VERTICAL);scroll.addView(items);
        item(items,"キーボードの大きさ/位置変更",adjust);
        item(items,"ユーザー辞書",dictionary);
        item(items,"その他の設定",settings);
    }
    private void item(LinearLayout items,String label,Runnable action){
        Button button=new Button(getContext());button.setText(label);button.setTextColor(Color.WHITE);
        button.setAllCaps(false);button.setBackgroundColor(Color.rgb(34,47,59));
        button.setOnClickListener(view->action.run());
        int height=Math.round(56*getResources().getDisplayMetrics().density);
        items.addView(button,new LayoutParams(-1,height));
    }
}
