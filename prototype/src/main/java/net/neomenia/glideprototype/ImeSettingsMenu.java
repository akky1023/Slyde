package net.neomenia.glideprototype;

import android.content.Context;
import android.view.View;
import androidx.appcompat.widget.PopupMenu;

final class ImeSettingsMenu {
    static PopupMenu show(Context context,View anchor,Runnable adjust,Runnable settings){
        PopupMenu menu=new PopupMenu(SettingsUi.themed(context),anchor);
        menu.getMenu().add(0,1,0,"大きさ・位置").setOnMenuItemClickListener(item->{adjust.run();return true;});
        menu.getMenu().add(0,2,1,"その他の設定").setOnMenuItemClickListener(item->{settings.run();return true;});
        menu.show();return menu;
    }
}
