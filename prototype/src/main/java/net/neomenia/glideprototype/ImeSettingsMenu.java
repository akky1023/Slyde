package net.neomenia.glideprototype;

import android.content.Context;
import android.graphics.Color;
import android.widget.*;

/** Settings replace both the candidate strip and the input keys inside the IME. */
final class ImeSettingsMenu extends LinearLayout {
    ImeSettingsMenu(Context context,Runnable back,Runnable adjust,Runnable settings){
        this(context,back,adjust,settings,()->{});
    }
    ImeSettingsMenu(Context context,Runnable back,Runnable adjust,Runnable settings,Runnable floatingChanged){
        super(context);setOrientation(VERTICAL);setBackgroundColor(Color.rgb(21,30,39));
        setContentDescription("キーボード設定一覧");
        int size=Math.round(48*getResources().getDisplayMetrics().density);
        ImageButton returnButton=UiIcons.button(context,R.drawable.ic_back,"戻る",back);
        addView(returnButton,new LayoutParams(size,size));
        ScrollView scroll=new ScrollView(context);addView(scroll,new LayoutParams(-1,0,1));
        LinearLayout items=new LinearLayout(context);items.setOrientation(VERTICAL);scroll.addView(items);
        item(items,"キーボードの大きさ/位置変更",adjust);
        item(items,"その他の設定",settings);
        androidx.appcompat.widget.SwitchCompat floating=new androidx.appcompat.widget.SwitchCompat(SettingsUi.themed(context));floating.setText("フローティングキーボード");floating.setTextColor(Color.WHITE);floating.setMinHeight(size);
        floating.setChecked(InputPreferences.get(context).getBoolean("floatingKeyboard",false));
        floating.setOnCheckedChangeListener((button,checked)->{InputPreferences.get(context).edit().putBoolean("floatingKeyboard",checked).apply();floatingChanged.run();});
        items.addView(floating,new LayoutParams(-1,-2));
    }
    private void item(LinearLayout items,String label,Runnable action){
        Button button=new Button(getContext());button.setText(label);button.setTextColor(Color.WHITE);
        button.setAllCaps(false);button.setBackgroundColor(Color.rgb(34,47,59));
        button.setOnClickListener(view->action.run());
        int height=Math.round(56*getResources().getDisplayMetrics().density);
        items.addView(button,new LayoutParams(-1,height));
    }
}
