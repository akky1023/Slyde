package net.neomenia.glideprototype;

import android.os.Bundle;
import android.view.*;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

/** Layout preview only; there is no text editor or practice input session. */
public final class LayoutSettingsActivity extends AppCompatActivity {
    private KeyboardDockView dock;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){cancel();}});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(SettingsUi.surface(this));setContentView(root);
        if(android.os.Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());root.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});}
        MaterialToolbar toolbar=new MaterialToolbar(this);toolbar.setTitle("大きさ・位置");toolbar.setNavigationIcon(R.drawable.ic_back);toolbar.setNavigationIconTint(com.google.android.material.color.MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurface,0));toolbar.setNavigationOnClickListener(view->cancel());root.addView(toolbar,new LinearLayout.LayoutParams(-1,SettingsUi.dp(this,64)));
        android.content.SharedPreferences preferences=InputPreferences.get(this);
        android.content.Context keyboardContext=new ContextThemeWrapper(this,android.R.style.Theme_Material_Light_NoActionBar);
        LinearLayout preview=new LinearLayout(keyboardContext);preview.setOrientation(LinearLayout.VERTICAL);
        CandidateBarView candidates=new CandidateBarView(keyboardContext,value->{},value->{},()->{});
        candidates.setTypeface(ImeFonts.load(this,preferences.getInt("imeFont",0)));candidates.setTextSp(preferences.getInt("candidateTextSp",18));candidates.setRows(preferences.getInt("candidateRows",1));
        int candidateHeight=Math.max(1,Math.min(3,preferences.getInt("candidateRows",1)))*candidates.rowHeight();preview.addView(candidates,new LinearLayout.LayoutParams(-1,candidateHeight));
        GlideKeyboardView keyboard=new GlideKeyboardView(keyboardContext,new GlideKeyboardView.Listener(){public void text(String text,boolean japanese){}public void action(String action){}public void preview(String route,String value){}});
        keyboard.setTypeface(ImeFonts.load(this,preferences.getInt("imeFont",0)));preview.addView(keyboard,new LinearLayout.LayoutParams(-1,SettingsUi.dp(this,232)));
        dock=new KeyboardDockView(this,preview,preferences,candidateHeight,height->{keyboard.getLayoutParams().height=Math.max(SettingsUi.dp(this,96),height-candidateHeight);},keyboard::setSideWidths);
        dock.setEditFinishedListener(this::finish);root.addView(dock,new LinearLayout.LayoutParams(-1,-2));dock.post(dock::edit);
    }
    private void cancel(){if(dock!=null)dock.cancelEdit();finish();}
}
