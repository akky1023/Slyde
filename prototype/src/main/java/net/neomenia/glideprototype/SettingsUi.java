package net.neomenia.glideprototype;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;

final class SettingsUi {
    static int dp(Context context,float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    static Context themed(Context context){return new ContextThemeWrapper(context,R.style.Theme_Glide_Settings);}
    static int surface(Context context){return MaterialColors.getColor(context,com.google.android.material.R.attr.colorSurface,0xfffafdfb);}
    static MaterialTextView text(Context context,String value,int size){
        MaterialTextView view=new MaterialTextView(context);view.setText(value);
        int appearance=size>=20?com.google.android.material.R.style.TextAppearance_Material3_TitleLarge:size>=16?com.google.android.material.R.style.TextAppearance_Material3_BodyLarge:size>=14?com.google.android.material.R.style.TextAppearance_Material3_BodyMedium:com.google.android.material.R.style.TextAppearance_Material3_BodySmall;
        view.setTextAppearance(context,appearance);
        view.setTextColor(MaterialColors.getColor(context,com.google.android.material.R.attr.colorOnSurface,0xff191c1b));return view;
    }
    static MaterialButton button(Context context,String value,Runnable action){
        MaterialButton button=new MaterialButton(context);button.setText(value);button.setAllCaps(false);button.setMinHeight(dp(context,48));
        button.setOnClickListener(view->action.run());return button;
    }
    static void addButton(LinearLayout parent,String value,Runnable action){
        MaterialButton button=new MaterialButton(parent.getContext(),null,com.google.android.material.R.attr.materialButtonOutlinedStyle);button.setText(value);button.setAllCaps(false);button.setMinHeight(dp(parent.getContext(),56));button.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);button.setOnClickListener(view->action.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(parent.getContext(),8);parent.addView(button,params);
    }
}
