package net.neomenia.glideprototype;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Settings replace both the candidate strip and the input keys inside the IME. */
final class ImeSettingsMenu extends LinearLayout {
    ImeSettingsMenu(Context context,Runnable back,Runnable adjust,Runnable settings){
        this(context,back,adjust,settings,()->{},()->{});
    }
    ImeSettingsMenu(Context context,Runnable back,Runnable adjust,Runnable settings,Runnable dictionary,Runnable floatingChanged){
        super(context);setOrientation(VERTICAL);
        setContentDescription("キーボード設定一覧");
        int size=Math.round(48*getResources().getDisplayMetrics().density);
        ImageButton returnButton=UiIcons.button(context,R.drawable.ic_back,"戻る",back);
        LinearLayout toolbar=new LinearLayout(context);toolbar.setOrientation(HORIZONTAL);addView(toolbar,new LayoutParams(-1,size));
        toolbar.addView(returnButton,new LayoutParams(size,size));toolbar.addView(new android.view.View(context),new LayoutParams(0,size,1));
        ImageButton floating=UiIcons.button(context,R.drawable.ic_floating_keyboard,"フローティングキーボード",()->{});
        Runnable refreshFloating=()->{
            boolean enabled=InputPreferences.get(context).getBoolean("floatingKeyboard",false);
            ImeColors colors=ImeColors.from(context);
            floating.setSelected(enabled);floating.setColorFilter(enabled?colors.accent():colors.neutralText());
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
        refreshColors();
    }
    void refreshColors(){
        ImeColors colors=ImeColors.from(getContext());setBackgroundColor(colors.surface());
        recolor(this,colors);
    }
    private void recolor(View view,ImeColors colors){
        if(view instanceof ImageButton button){
            button.setBackgroundColor(colors.key());button.setColorFilter(button.isSelected()?colors.accent():colors.neutralText());
        }else if(view instanceof Button button){
            button.setBackgroundColor(colors.key());button.setTextColor(colors.neutralText());
        }
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)recolor(group.getChildAt(i),colors);
    }
    private void item(LinearLayout items,String label,Runnable action){
        Button button=new Button(getContext());button.setText(label);button.setAllCaps(false);
        button.setOnClickListener(view->action.run());
        int height=Math.round(56*getResources().getDisplayMetrics().density);
        items.addView(button,new LayoutParams(-1,height));
    }
}
