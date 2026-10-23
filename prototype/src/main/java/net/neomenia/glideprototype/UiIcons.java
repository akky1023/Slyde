package net.neomenia.glideprototype;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.widget.ImageButton;

/** Native vector rendering of the SVG sources in prototype/artwork/icons. */
final class UiIcons {
    static Drawable drawable(Context context,int resource){return context.getDrawable(resource).mutate();}
    static ImageButton button(Context context,int resource,String description,Runnable action){
        ImageButton button=new ImageButton(context);button.setImageDrawable(drawable(context,resource));
        button.setContentDescription(description);button.setTooltipText(description);
        button.setColorFilter(Color.WHITE);button.setBackgroundColor(Color.rgb(34,47,59));
        int padding=Math.round(12*context.getResources().getDisplayMetrics().density);button.setPadding(padding,padding,padding,padding);
        button.setOnClickListener(view->action.run());return button;
    }
    static int category(String name){
        return switch(name){
            case "最近使った記号" -> R.drawable.ic_history;
            case "よく使う記号" -> R.drawable.ic_common;
            case "半角記号" -> R.drawable.ic_halfwidth;
            case "全角記号・括弧" -> R.drawable.ic_fullwidth;
            case "矢印" -> R.drawable.ic_arrows;
            case "数学" -> R.drawable.ic_math;
            case "通貨・単位" -> R.drawable.ic_currency;
            case "単位・囲み文字" -> R.drawable.ic_units;
            case "図形・罫線" -> R.drawable.ic_shapes;
            case "絵文字：顔・感情" -> R.drawable.ic_face;
            case "絵文字：人・手" -> R.drawable.ic_people;
            case "絵文字：肌・髪" -> R.drawable.ic_component;
            case "絵文字：動物・自然" -> R.drawable.ic_nature;
            case "絵文字：食べ物" -> R.drawable.ic_food;
            case "絵文字：乗り物・場所" -> R.drawable.ic_travel;
            case "絵文字：活動" -> R.drawable.ic_activity;
            case "絵文字：道具" -> R.drawable.ic_objects;
            case "絵文字：旗" -> R.drawable.ic_flags;
            default -> R.drawable.ic_symbols;
        };
    }
}
