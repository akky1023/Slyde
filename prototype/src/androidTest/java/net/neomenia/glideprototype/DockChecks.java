package net.neomenia.glideprototype;

import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.util.List;

/** Device checks for the actual crop controls, settings visibility and saved geometry. */
final class DockChecks {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void layout(View view,int width,int height){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.AT_MOST));view.layout(0,0,width,view.getMeasuredHeight());}
    private static void click(View view,String label){
        if(view instanceof Button button&&label.contentEquals(button.getText())){button.performClick();return;}
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)click(group.getChildAt(i),label);
    }
    private static void move(View overlay,float x,float y,float dx,float dy){
        long now=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){
            MotionEvent event=MotionEvent.obtain(now,now,action,action==MotionEvent.ACTION_DOWN?x:x+dx,action==MotionEvent.ACTION_DOWN?y:y+dy,0);overlay.dispatchTouchEvent(event);event.recycle();
        }
    }
    private static void pinch(View overlay,float x,float y,float density){
        long now=SystemClock.uptimeMillis();
        for(int step=0;step<7;step++){
            int count=step==0||step==6?1:2;
            int action=step==0?MotionEvent.ACTION_DOWN:step==1?MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT):step==5?MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT):step==6?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
            float span=(step<2?80:step==2?65:step==3?48:32)*density;
            MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
            for(int i=0;i<count;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=x+(i==0?-span:span);coords[i].y=y;coords[i].pressure=1;coords[i].size=1;}
            MotionEvent event=MotionEvent.obtain(now,now+step*20,action,count,properties,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);overlay.dispatchTouchEvent(event);event.recycle();
        }
    }
    static void run(Context context){
        android.content.res.Configuration configuration=new android.content.res.Configuration(context.getResources().getConfiguration());
        configuration.orientation=android.content.res.Configuration.ORIENTATION_PORTRAIT;configuration.screenHeightDp=800;configuration.screenWidthDp=360;context=context.createConfigurationContext(configuration);
        float d=context.getResources().getDisplayMetrics().density;int width=Math.round(360*d),height=Math.round(800*d),row=Math.round(44*d);
        SharedPreferences preferences=context.getSharedPreferences("dock-device-check",Context.MODE_PRIVATE);preferences.edit().clear().commit();
        View keyboard=new View(context);KeyboardDockView dock=new KeyboardDockView(context,keyboard,preferences,row,value->{});
        layout(dock,width,height);int initialHeight=dock.getHeight();check(dock.getHeight()-keyboard.getBottom()>=Math.round(12*d),"missing bottom gap");
        int originalKeysHeight=keyboard.getHeight();
        for(int pass=0;pass<40;pass++){
            layout(dock,width,dock.getHeight());
            check(dock.getHeight()==initialHeight&&keyboard.getHeight()==originalKeysHeight,"IME second measure must not shrink normal keyboard");
        }
        dock.edit();layout(dock,width,height);int top=keyboard.getTop()-dock.toolbarHeight();
        move(dock.getChildAt(1),width/2f,top+keyboard.getHeight()/2f,0,-30*d);layout(dock,width,height);
        click(dock,"保存");layout(dock,width,height);check(!dock.isEditing(),"save must exit editor");check(dock.getHeight()>initialHeight,"moving upward must enlarge bottom gap");
        int savedKeysHeight=keyboard.getHeight();
        for(int pass=0;pass<40;pass++){int allocated=dock.getHeight();layout(dock,width,allocated);check(dock.getHeight()==allocated&&keyboard.getHeight()==savedKeysHeight,"saved keyboard must match editor size after repeated IME measurements");}
        int savedHeight=dock.getHeight();View restoredKeyboard=new View(context);KeyboardDockView restored=new KeyboardDockView(context,restoredKeyboard,preferences,row,value->{});layout(restored,width,height);check(restored.getHeight()==savedHeight,"position must survive recreation");
        dock.edit();layout(dock,width,height);top=keyboard.getTop()-dock.toolbarHeight();
        move(dock.getChildAt(1),0,top+keyboard.getHeight()/2f,40*d,0);layout(dock,width,height);check(keyboard.getWidth()<width,"left handle must resize");
        click(dock,"取消");layout(dock,width,height);check(keyboard.getWidth()==width&&dock.getHeight()==savedHeight,"cancel must restore saved rectangle");
        dock.edit();layout(dock,width,height);
        dock.setSideWidthPercents(8,25);click(dock,"保存");layout(dock,width,height);
        check(preferences.getInt("dockPortrait.leftSidePercent",0)==8&&preferences.getInt("dockPortrait.rightSidePercent",0)==25,"independent side widths must save");
        dock.edit();layout(dock,width,height);dock.setSideWidthPercents(25,8);click(dock,"取消");layout(dock,width,height);
        check(preferences.getInt("dockPortrait.leftSidePercent",0)==8&&preferences.getInt("dockPortrait.rightSidePercent",0)==25,"cancel must restore saved side widths");
        dock.edit();layout(dock,width,height);top=keyboard.getTop()-dock.toolbarHeight();
        move(dock.getChildAt(1),0,top+keyboard.getHeight()/2f,40*d,0);layout(dock,width,height);
        int movedWidth=keyboard.getWidth();click(dock,"中央寄せ");layout(dock,width,height);
        check(keyboard.getWidth()==movedWidth&&Math.abs(keyboard.getLeft()*2+keyboard.getWidth()-width)<=1,"center must preserve keyboard size");
        int beforePinch=keyboard.getWidth();pinch(dock.getChildAt(1),keyboard.getLeft()+keyboard.getWidth()/2f,keyboard.getTop()-dock.toolbarHeight()+keyboard.getHeight()/2f,d);layout(dock,width,height);
        check(keyboard.getWidth()<beforePinch,"two-pointer pinch must resize the actual keyboard frame");
        click(dock,"取消");layout(dock,width,height);
        if(android.os.Build.VERSION.SDK_INT>=30){
            int stableHeight=savedHeight,barPixels=Math.round(48*d);
            for(int frame=0;frame<120;frame++){
                WindowInsets delivered=new WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.navigationBars(),android.graphics.Insets.of(0,0,0,barPixels))
                    .setInsets(WindowInsets.Type.ime(),android.graphics.Insets.of(0,0,0,frame%2==0?height:height/2)).build();
                dock.dispatchApplyWindowInsets(delivered);layout(dock,width,height);
                int parentOffset=frame%2==0?0:Math.round(13*d);
                dock.layout(0,parentOffset,width,parentOffset+dock.getMeasuredHeight());
                dock.getViewTreeObserver().dispatchOnGlobalLayout();
                check(dock.getHeight()==stableHeight,"unchanged navigation insets must keep idle height stable");
                check(!dock.isLayoutRequested(),"global layout must not schedule a padding feedback loop");
            }
            WindowInsets consumed=new WindowInsets.Builder().setInsets(WindowInsets.Type.navigationBars(),android.graphics.Insets.NONE).build();
            dock.dispatchApplyWindowInsets(consumed);layout(dock,width,height);check(dock.getHeight()==savedHeight,"navigation insets must not affect manually configured gap");
        }
        CandidateBarView bar=new CandidateBarView(context,value->{},value->{});bar.setRows(3);layout(bar,width,height);
        bar.setCandidates(List.of("日本語の変換候補","文字サイズ"));bar.setTextSp(12);layout(bar,width,height);int smallHeight=bar.getHeight();
        checkCandidateText(bar,12*context.getResources().getDisplayMetrics().scaledDensity);
        bar.setTextSp(18);layout(bar,width,height);int mediumHeight=bar.getHeight();
        check(mediumHeight>smallHeight,"candidate rows must shrink with smaller text even below 44dp");
        checkCandidateText(bar,18*context.getResources().getDisplayMetrics().scaledDensity);
        bar.setTextSp(30);layout(bar,width,height);check(bar.getHeight()>mediumHeight,"larger candidate text must receive sufficient row height");
        checkCandidateText(bar,30*context.getResources().getDisplayMetrics().scaledDensity);
        int compactHeight=bar.getHeight();bar.setKeyboardHeight(Math.round(232*d));bar.setExpanded(true);layout(bar,width,height);
        check(bar.getHeight()==compactHeight+Math.round(232*d),"expanded candidates must preserve configured row height");checkCandidateText(bar,30*context.getResources().getDisplayMetrics().scaledDensity);
        bar.setTextSp(12);layout(bar,width,height);check(bar.getHeight()==smallHeight+Math.round(232*d),"expanded candidates must shrink with smaller text");checkCandidateText(bar,12*context.getResources().getDisplayMetrics().scaledDensity);
        bar.setExpanded(false);bar.setCandidates(List.of());bar.setRows(1);layout(bar,width,height);
        int[] fontHeights=new int[31];
        for(int font=0;font<ImeFonts.NAMES.length;font++)for(int size:new int[]{12,18,30}){
            bar.setTypeface(ImeFonts.load(context,font));bar.setTextSp(size);
            if(font==0)fontHeights[size]=bar.rowHeight();
            else check(bar.rowHeight()==fontHeights[size],"switching fonts must not change candidate row height");
            bar.setCandidates(List.of("国","日本語の候補","AgÉgj","かなカナ","😀"),"日本語の候補");
            for(int lines:new int[]{1,3})for(boolean expanded:new boolean[]{false,true}){
                bar.setRows(lines);bar.setExpanded(expanded);layout(bar,width,height);
                for(boolean characters:new boolean[]{false,true})for(boolean ai:new boolean[]{false,true}){
                    bar.setAnnotationVisibility(characters,ai);layout(bar,width,height);
                    checkCandidateText(bar,size*context.getResources().getDisplayMetrics().scaledDensity);
                }
            }
        }
        preferences.edit().clear().commit();
        FrameLayout touchHost=new FrameLayout(context);String[] emitted={"",""};
        GlideKeyboardView keypad=new GlideKeyboardView(context,new GlideKeyboardView.Listener(){
            public void text(String raw,boolean japanese){emitted[0]=raw;}
            public void action(String action){emitted[1]=action;}
            public void preview(String route,String value){}
        });touchHost.addView(keypad);layout(keypad,width,Math.round(232*d));keypad.setSideWidths(8,25);
        move(keypad,width*.12f,keypad.getHeight()/8f,0,0);check(emitted[0].equals("a"),"narrow left sidebar must expand the letter hit area");
        move(keypad,width*.8f,keypad.getHeight()/8f,0,0);check(emitted[1].equals("delete"),"wide right sidebar must expand the action hit area");
        keypad.setSideWidths(25,8);emitted[1]="";move(keypad,width*.2f,keypad.getHeight()*3/8f,0,0);
        check(emitted[1].equals("left"),"wide left sidebar must use the new action boundary");keypad.cancelTouches();
        WidthLabelView note=new WidthLabelView(context);note.useCandidateTypography();note.setValue("二");note.setAi(true);layout(note,width,bar.rowHeight());
        int annotatedBaseline=note.getBaseline();check(note.getContentDescription().toString().contains("[漢]")&&note.getContentDescription().toString().contains("AI候補"),"enabled annotations must be exposed");
        note.setAnnotationVisibility(false,false);check(note.getContentDescription().toString().equals("二"),"disabled annotations must be hidden");check(note.getBaseline()==annotatedBaseline,"hiding annotations must not move candidate text");
    }
    private static SeekBar findSlider(View view,String name){
        if(view instanceof SeekBar slider&&name.contentEquals(slider.getContentDescription()))return slider;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){SeekBar found=findSlider(group.getChildAt(i),name);if(found!=null)return found;}
        return null;
    }
    private static void checkCandidateText(View view,float expectedSize){
        if(view instanceof WidthLabelView text){
            check(Math.abs(text.getTextSize()-expectedSize)<1,"candidate font size must apply in compact and expanded views");
            check(!text.getIncludeFontPadding(),"font-wide top/bottom padding must not offset candidate text");
            int bodyHeight=text.getHeight()-text.getPaddingTop()-text.getPaddingBottom();
            android.graphics.Rect bounds=new android.graphics.Rect();text.getPaint().getTextBounds("国",0,1,bounds);
            float lineCenter=text.getBaseline()+(bounds.top+bounds.bottom)/2f;
            float bodyCenter=text.getPaddingTop()+bodyHeight/2f;
            check(Math.abs(lineCenter-bodyCenter)<=1,"Japanese glyph must be centered independently of font metrics");
            String value=text.getText().toString();text.getPaint().getTextBounds(value,0,value.length(),bounds);
            check(text.getBaseline()+bounds.top>=text.getPaddingTop()-1&&text.getBaseline()+bounds.bottom<=text.getHeight()-text.getPaddingBottom()+1,"candidate glyphs must fit above the annotation");
            if(value.equals("国"))checkRenderedCenter(text,bodyCenter);
        }
        if(view instanceof ViewGroup group)for(int index=0;index<group.getChildCount();index++)checkCandidateText(group.getChildAt(index),expectedSize);
    }
    private static void checkRenderedCenter(WidthLabelView text,float expectedCenter){
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(text.getWidth(),text.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
        try{
            text.draw(new android.graphics.Canvas(bitmap));int top=bitmap.getHeight(),bottom=-1,color=text.getCurrentTextColor();
            for(int y=text.getPaddingTop();y<bitmap.getHeight()-text.getPaddingBottom();y++)for(int x=0;x<bitmap.getWidth();x++){
                int pixel=bitmap.getPixel(x,y);
                if(android.graphics.Color.alpha(pixel)>128&&(pixel&0xffffff)==(color&0xffffff)){top=Math.min(top,y);bottom=Math.max(bottom,y);}
            }
            check(bottom>=top,"candidate text must actually be painted");
            check(Math.abs((top+bottom+1)/2f-expectedCenter)<=2,"painted Japanese candidate must be vertically centered");
        }finally{bitmap.recycle();}
    }
}
