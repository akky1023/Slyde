package net.neomenia.glideprototype;

import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;
import java.util.function.BiConsumer;
import net.neomenia.inputcore.KeyboardGeometry;
import net.neomenia.inputcore.KeyboardGeometry.Rect;
import net.neomenia.inputcore.KeyboardSizing;

/** Adjust the real keyboard inside its IME window, without replacing the host app. */
final class KeyboardDockView extends ViewGroup {
    private final View keyboard;
    private final SharedPreferences preferences;
    private final IntConsumer resized;
    private final LinearLayout toolbar;
    private final Handles handles;
    private final BiConsumer<Integer,Integer> sideWidths;
    private final Button sideWidthButton;
    private final TextView hint;
    private boolean adjustingSides;
    private int leftPercent=15,rightPercent=15;
    private Runnable editFinished=()->{};
    private String sideKey;
    // Bounds describe only the key surface; the candidate strip sits above this canvas.
    private Rect bounds;
    private boolean editing;
    private int candidateHeight,areaHeight,lastWidth;
    private boolean floating;
    private final Boolean layoutMode;
    private final View moveHandle;
    private final QuickHandles quickHandles;
    private final android.os.Handler quickHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable hideQuickHandles=this::hideQuickHandles;
    private final float density;
    private ImeColors colors;
    private String key(){return (floating?"floating":"dock")+(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?"Landscape.":"Portrait.");}
    private int dp(float value){return Math.round(value*density);}
    KeyboardDockView(Context context,View keyboard,SharedPreferences preferences,int candidateHeight,IntConsumer resized){
        this(context,keyboard,preferences,candidateHeight,resized,(left,right)->{});
    }
    KeyboardDockView(Context context,View keyboard,SharedPreferences preferences,int candidateHeight,IntConsumer resized,BiConsumer<Integer,Integer> sideWidths){
        this(context,keyboard,preferences,candidateHeight,resized,sideWidths,null);
    }
    // A fixed mode lets each settings preview edit its own layout without switching the IME.
    KeyboardDockView(Context context,View keyboard,SharedPreferences preferences,int candidateHeight,IntConsumer resized,BiConsumer<Integer,Integer> sideWidths,Boolean layoutMode){
        super(context);this.keyboard=keyboard;this.preferences=preferences;this.candidateHeight=candidateHeight;this.resized=resized;this.sideWidths=sideWidths;density=getResources().getDisplayMetrics().density;
        this.layoutMode=layoutMode;floating=layoutMode!=null?layoutMode:preferences.getBoolean("floatingKeyboard",false);
        colors=ImeColors.from(context);
        setBackgroundColor(floating?Color.TRANSPARENT:colors.surface());addView(keyboard);
        handles=new Handles(context);addView(handles);
        Context settingsContext=SettingsUi.themed(context);
        toolbar=new LinearLayout(settingsContext);toolbar.setOrientation(LinearLayout.VERTICAL);toolbar.setBackgroundColor(SettingsUi.surface(settingsContext));
        hint=SettingsUi.text(settingsContext,"枠の中で移動 · 辺や2本指でサイズ変更",13);hint.setGravity(Gravity.CENTER);toolbar.addView(hint,new LinearLayout.LayoutParams(-1,dp(24)));
        sideWidthButton=SettingsUi.button(settingsContext,"サイドバーの幅",()->{adjustingSides=!adjustingSides;updateSideControls();});toolbar.addView(sideWidthButton,new LinearLayout.LayoutParams(-1,dp(56)));
        LinearLayout actions=new LinearLayout(settingsContext);toolbar.addView(actions,new LinearLayout.LayoutParams(-1,dp(48)));
        control(actions,"取消",()->endEdit(false));control(actions,"初期位置",()->{bounds=defaults(lastWidth);setSideWidthPercents(15,15);requestLayout();});
        control(actions,"中央寄せ",()->{if(bounds!=null){bounds=new Rect((lastWidth-bounds.width())/2,bounds.top(),bounds.width(),bounds.height());requestLayout();}});control(actions,"保存",()->endEdit(true));
        addView(toolbar);toolbar.setVisibility(GONE);handles.setVisibility(GONE);
        moveHandle=new View(context){
            private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){paint.setColor(colors.text());float half=Math.min(dp(24),getWidth()/4f),y=getHeight()/2f;canvas.drawRoundRect(getWidth()/2f-half,y-dp(2),getWidth()/2f+half,y+dp(2),dp(2),dp(2),paint);}
        };moveHandle.setBackgroundColor(colors.key());moveHandle.setContentDescription("キーボードを移動。押すと四隅でサイズ変更");
        moveHandle.setOnTouchListener(new OnTouchListener(){
            private float x,y;private Rect initial;private int pointer=-1;
            @Override public boolean onTouch(View view,MotionEvent event){
                int action=event.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN){showQuickHandles();pointer=event.getPointerId(0);x=event.getRawX();y=event.getRawY();initial=bounds;getParent().requestDisallowInterceptTouchEvent(true);}
                if(action==MotionEvent.ACTION_MOVE&&event.getPointerId(0)==pointer&&initial!=null){bounds=dragFloating(initial,0,event.getRawX()-x,event.getRawY()-y);requestLayout();}
                if(action==MotionEvent.ACTION_CANCEL&&initial!=null){bounds=initial;requestLayout();pointer=-1;scheduleHideQuickHandles();}
                if(action==MotionEvent.ACTION_UP){saveBounds();pointer=-1;view.performClick();scheduleHideQuickHandles();}
                return true;
            }
        });addView(moveHandle);moveHandle.setVisibility(floating?VISIBLE:GONE);
        quickHandles=new QuickHandles(context);addView(quickHandles);quickHandles.setVisibility(GONE);
        loadSideWidths();
    }
    boolean isFloating(){return floating;}
    void setFloating(boolean value){if(floating==value)return;cancelEdit();hideQuickHandles();floating=value;preferences.edit().putBoolean("floatingKeyboard",value).apply();bounds=null;lastWidth=0;refreshColors();moveHandle.setVisibility(value?VISIBLE:GONE);loadSideWidths();requestLayout();}
    android.graphics.Rect touchBounds(){return editing?new android.graphics.Rect(0,0,getWidth(),getHeight()):new android.graphics.Rect(keyboard.getLeft(),keyboard.getTop(),keyboard.getRight(),floating?moveHandle.getBottom():keyboard.getBottom());}
    Region touchRegion(){
        Region region=new Region(touchBounds());
        if(quickHandles.getVisibility()==VISIBLE){
            for(int corner=0;corner<4;corner++)region.op(quickHandles.touchRect(corner),Region.Op.UNION);
            region.op(0,0,getWidth(),getHeight(),Region.Op.INTERSECT);
        }
        return region;
    }
    private void showQuickHandles(){quickHandler.removeCallbacks(hideQuickHandles);quickHandles.setVisibility(VISIBLE);quickHandles.invalidate();}
    private void scheduleHideQuickHandles(){quickHandler.removeCallbacks(hideQuickHandles);quickHandler.postDelayed(hideQuickHandles,3000);}
    private void hideQuickHandles(){quickHandler.removeCallbacks(hideQuickHandles);quickHandles.setVisibility(GONE);invalidate();}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&floating&&!editing&&quickHandles.getVisibility()==VISIBLE){
            float x=event.getX(),y=event.getY();
            if(x>=keyboard.getLeft()&&x<keyboard.getRight()&&y>=keyboard.getTop()&&y<keyboard.getBottom()&&quickHandles.corner(x,y)==0)hideQuickHandles();
        }
        return super.dispatchTouchEvent(event);
    }
    private int moveHeight(){return floating&&!editing?dp(28):0;}
    int toolbarHeight(){return dp(128);}
    void setEditFinishedListener(Runnable action){editFinished=action;}
    void setSideWidthPercents(int left,int right){leftPercent=Math.max(8,Math.min(25,left));rightPercent=Math.max(8,Math.min(25,right));updateSideControls();sideWidths.accept(leftPercent,rightPercent);}
    private void updateSideControls(){sideWidthButton.setText((adjustingSides?"枠の調整に戻る":"サイドバーの幅")+"　左 "+leftPercent+"%・右 "+rightPercent+"%");hint.setText(adjustingSides?"左右の青い境界線をドラッグして幅を調整":"枠の中で移動 · 辺や2本指でサイズ変更");handles.setContentDescription(adjustingSides?"左右のサイドバー境界線。ドラッグして幅を変更":"キーボード調整枠。内側で移動、四隅と辺または2本指でサイズ変更");handles.invalidate();}
    private void loadSideWidths(){sideKey=key();setSideWidthPercents(preferences.getInt(key()+"leftSidePercent",15),preferences.getInt(key()+"rightSidePercent",15));}
    private void control(LinearLayout row,String label,Runnable action){
        com.google.android.material.button.MaterialButton button=SettingsUi.button(row.getContext(),label,action);button.setTextSize(14);button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(dp(4),0,dp(4),0);row.addView(button,new LinearLayout.LayoutParams(0,-1,1));
    }
    void setCandidateHeight(int value){if(candidateHeight!=value){candidateHeight=value;requestLayout();}}
    private void refreshColors(){colors=ImeColors.from(getContext());setBackgroundColor(floating?Color.TRANSPARENT:colors.surface());moveHandle.setBackgroundColor(colors.key());moveHandle.invalidate();}
    void reloadPreferences(){refreshColors();if(editing)return;if(layoutMode==null)setFloating(preferences.getBoolean("floatingKeyboard",false));loadSideWidths();bounds=null;requestLayout();}
    boolean isEditing(){return editing;}
    void edit(){if(editing)return;hideQuickHandles();adjustingSides=false;loadSideWidths();editing=true;bounds=null;moveHandle.setVisibility(GONE);toolbar.setVisibility(VISIBLE);handles.setVisibility(VISIBLE);requestLayout();announceForAccessibility("キーボードの大きさと位置を調整。枠をドラッグ、または2本指で拡大縮小できます。");}
    void cancelEdit(){if(editing)endEdit(false);}
    private void endEdit(boolean save){
        if(save)saveBounds();
        loadSideWidths();
        editing=false;bounds=null;toolbar.setVisibility(GONE);handles.setVisibility(GONE);moveHandle.setVisibility(floating?VISIBLE:GONE);requestLayout();
        editFinished.run();
    }
    private void saveBounds(){
        if(bounds!=null){
            preferences.edit().putFloat(key()+"width",bounds.width()/Math.max(1,lastWidth))
                .putFloat(key()+"left",lastWidth>bounds.width()?bounds.left()/(lastWidth-bounds.width()):.5f)
                .putFloat(key()+"keyHeight",bounds.height()/density).putFloat(key()+"bottom",(areaHeight-bounds.bottom())/density)
                .putInt(key()+"leftSidePercent",leftPercent).putInt(key()+"rightSidePercent",rightPercent).apply();
        }
    }
    private float minHeight(){return dp(4*32);}
    private float minWidth(){return Math.min(dp(240),lastWidth*.7f);}
    private Rect defaults(int width){float w=floating?Math.min(dp(360),width*.8f):width;return fit(new Rect((width-w)/2,areaHeight-dp(232+24),w,dp(232)),width);}
    private int floatingMargin(){return floating&&!editing?dp(12):0;}
    private Rect fit(Rect r,int width){
        int margin=floatingMargin();
        Rect fitted=KeyboardGeometry.fit(new Rect(r.left()-margin,r.top()-margin,r.width(),r.height()),width-2*margin,areaHeight-2*margin,minWidth(),minHeight(),dp(12));
        return new Rect(fitted.left()+margin,fitted.top()+margin,fitted.width(),fitted.height());
    }
    private Rect dragFloating(Rect r,int edges,float dx,float dy){
        int margin=floatingMargin();
        Rect dragged=KeyboardGeometry.drag(new Rect(r.left()-margin,r.top()-margin,r.width(),r.height()),edges,dx,dy,lastWidth-2*margin,areaHeight-2*margin,minWidth(),minHeight(),dp(12));
        return new Rect(dragged.left()+margin,dragged.top()+margin,dragged.width(),dragged.height());
    }
    private Rect load(int width){
        // Older saved heights included the candidate strip.
        float legacyHeight=preferences.getFloat(key()+"height",candidateHeight/density+232)-candidateHeight/density;
        if(!preferences.contains(key()+"keyHeight")&&preferences.contains(key()+"height"))
            preferences.edit().putFloat(key()+"keyHeight",legacyHeight).apply();
        float w=width*preferences.getFloat(key()+"width",floating?Math.min(dp(360)/(float)Math.max(1,width),.8f):1),h=dp(preferences.getFloat(key()+"keyHeight",legacyHeight));
        float gap=dp(preferences.getFloat(key()+"bottom",24));
        return fit(new Rect((width-w)*preferences.getFloat(key()+"left",.5f),areaHeight-gap-h,w,h),width);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        if(!key().equals(sideKey))loadSideWidths();
        int width=MeasureSpec.getSize(widthSpec);int controls=toolbarHeight();
        int available=dp(getResources().getConfiguration().screenHeightDp*(floating?1f:.64f));
        if(MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED)available=Math.min(available,MeasureSpec.getSize(heightSpec));
        // The IME can measure WRAP_CONTENT again using the height from its first pass.
        // A hidden toolbar must not be deducted from that already allocated keyboard height.
        int nextHeight=KeyboardSizing.canvasHeight(available,dp(12)+candidateHeight+dp(96),controls,editing)-candidateHeight-moveHeight();
        if(lastWidth!=width||areaHeight!=nextHeight){
            if(bounds!=null&&lastWidth>0){
                float fraction=bounds.width()/lastWidth,anchor=lastWidth>bounds.width()?bounds.left()/(lastWidth-bounds.width()):.5f,gap=areaHeight-bounds.bottom();
                bounds=new Rect((width-width*fraction)*anchor,nextHeight-gap-bounds.height(),width*fraction,bounds.height());
            }
            lastWidth=width;areaHeight=nextHeight;
        }
        if(bounds==null)bounds=load(width);else bounds=fit(bounds,width);
        int height=candidateHeight+moveHeight()+(editing?areaHeight+controls:floating?areaHeight:Math.round(areaHeight-bounds.top()));
        int contentHeight=Math.round(bounds.height())+candidateHeight;
        resized.accept(contentHeight);
        keyboard.measure(MeasureSpec.makeMeasureSpec(Math.round(bounds.width()),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(contentHeight,MeasureSpec.EXACTLY));
        toolbar.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(controls,MeasureSpec.EXACTLY));
        handles.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(areaHeight,MeasureSpec.EXACTLY));
        moveHandle.measure(MeasureSpec.makeMeasureSpec(Math.round(bounds.width()),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(moveHeight(),MeasureSpec.EXACTLY));
        quickHandles.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
        setMeasuredDimension(width,height);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int top=editing?toolbarHeight()+Math.round(bounds.top()):floating?Math.round(bounds.top()):0,left=Math.round(bounds.left());
        boolean moved=keyboard.getLeft()!=left||keyboard.getTop()!=top||keyboard.getWidth()!=Math.round(bounds.width())||keyboard.getHeight()!=candidateHeight+Math.round(bounds.height());
        keyboard.layout(left,top,left+Math.round(bounds.width()),top+candidateHeight+Math.round(bounds.height()));
        moveHandle.layout(left,keyboard.getBottom(),left+Math.round(bounds.width()),keyboard.getBottom()+moveHeight());
        quickHandles.layout(0,0,getWidth(),getHeight());if(quickHandles.getVisibility()==VISIBLE)quickHandles.invalidate();
        toolbar.layout(0,0,getWidth(),toolbarHeight());handles.layout(0,toolbarHeight()+candidateHeight,getWidth(),toolbarHeight()+candidateHeight+areaHeight);if(editing)handles.invalidate();
        if(floating&&moved)invalidate();
    }
    @Override protected void dispatchDraw(Canvas canvas){
        if(floating){
            // Drawing a transparent ColorDrawable uses SRC_OVER and cannot erase
            // pixels left by the previous position on a retained window buffer.
            int save=canvas.save();canvas.clipRect(0,0,getWidth(),getHeight());
            canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);canvas.restoreToCount(save);
        }
        super.dispatchDraw(canvas);
    }
    @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(-1,-2);}
    @Override protected void onDetachedFromWindow(){hideQuickHandles();super.onDetachedFromWindow();}
    private final class QuickHandles extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private Rect initial;
        private float downX,downY;
        private int pointer=-1,edges;
        QuickHandles(Context context){super(context);setContentDescription("キーボードの四隅をドラッグしてサイズ変更");}
        private float cornerX(int corner){return corner%2==0?keyboard.getLeft()-dp(8):keyboard.getRight()+dp(8);}
        private float cornerY(int corner){return corner<2?keyboard.getTop()-dp(8):moveHandle.getBottom()+dp(8);}
        android.graphics.Rect touchRect(int corner){int x=Math.round(cornerX(corner)),y=Math.round(cornerY(corner)),hit=dp(24);return new android.graphics.Rect(x-hit,y-hit,x+hit,y+hit);}
        private int corner(float x,float y){
            for(int corner=0;corner<4;corner++)if(touchRect(corner).contains((int)x,(int)y))return (corner%2==0?KeyboardGeometry.LEFT:KeyboardGeometry.RIGHT)|(corner<2?KeyboardGeometry.TOP:KeyboardGeometry.BOTTOM);
            return 0;
        }
        @Override protected void onDraw(Canvas canvas){
            paint.setColor(Color.rgb(104,181,255));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(4));paint.setStrokeCap(Paint.Cap.ROUND);
            float arm=dp(16);
            for(int i=0;i<4;i++){float x=cornerX(i),y=cornerY(i),dx=i%2==0?arm:-arm,dy=i<2?arm:-arm;canvas.drawLine(x,y,x+dx,y,paint);canvas.drawLine(x,y,x,y+dy,paint);}
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                edges=corner(event.getX(),event.getY());if(edges==0)return false;
                pointer=event.getPointerId(0);downX=event.getX();downY=event.getY();initial=bounds;
                quickHandler.removeCallbacks(hideQuickHandles);getParent().requestDisallowInterceptTouchEvent(true);return true;
            }
            if(pointer<0)return false;
            int index=event.findPointerIndex(pointer);
            if(action==MotionEvent.ACTION_MOVE&&index>=0){
                // Track the finger directly, without a long press, animation, or rate limit.
                bounds=dragFloating(initial,edges,event.getX(index)-downX,event.getY(index)-downY);
                KeyboardDockView.this.requestLayout();KeyboardDockView.this.invalidate();
            }
            if(action==MotionEvent.ACTION_CANCEL){bounds=initial;KeyboardDockView.this.requestLayout();pointer=-1;scheduleHideQuickHandles();}
            if(action==MotionEvent.ACTION_UP||(action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(event.getActionIndex())==pointer)){saveBounds();pointer=-1;scheduleHideQuickHandles();performClick();}
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private final class Handles extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private Rect initial;
        private float downX,downY;
        private int edges,pointer=-1;
        private int draggingSide,initialLeft,initialRight;
        private boolean pinching;
        private final ScaleGestureDetector scaleDetector;
        Handles(Context context){super(context);setContentDescription("キーボード調整枠。内側で移動、四隅と辺または2本指でサイズ変更");
            scaleDetector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScaleBegin(ScaleGestureDetector detector){pinching=bounds!=null&&detector.getFocusX()>=bounds.left()&&detector.getFocusX()<=bounds.right()&&detector.getFocusY()>=bounds.top()&&detector.getFocusY()<=bounds.bottom();return pinching;}
                @Override public boolean onScale(ScaleGestureDetector detector){bounds=KeyboardGeometry.scale(bounds,detector.getScaleFactor(),getWidth(),areaHeight,minWidth(),minHeight(),dp(12));KeyboardDockView.this.requestLayout();invalidate();return true;}
            });scaleDetector.setQuickScaleEnabled(false);
        }
        @Override protected void onDraw(Canvas canvas){
            if(bounds==null)return;
            float l=bounds.left(),t=bounds.top(),r=bounds.right(),b=bounds.bottom();
            paint.setColor(Color.argb(110,0,0,0));canvas.drawRect(0,0,getWidth(),t,paint);canvas.drawRect(0,b,getWidth(),getHeight(),paint);canvas.drawRect(0,t,l,b,paint);canvas.drawRect(r,t,getWidth(),b,paint);
            paint.setColor(Color.rgb(104,181,255));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawRect(l+dp(1),t+dp(1),r-dp(1),b-dp(1),paint);
            paint.setColor(Color.argb(90,255,255,255));paint.setStrokeWidth(dp(1));
            for(int i=1;i<3;i++){canvas.drawLine(l+(r-l)*i/3,t,l+(r-l)*i/3,b,paint);canvas.drawLine(l,t+(b-t)*i/3,r,t+(b-t)*i/3,paint);}
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(104,181,255));
            if(adjustingSides){
                float keysTop=t,middle=(keysTop+b)/2;
                paint.setColor(Color.rgb(104,181,255));paint.setStrokeWidth(dp(4));
                for(float x:new float[]{l+(r-l)*leftPercent/100f,r-(r-l)*rightPercent/100f}){
                    canvas.drawLine(x,keysTop,x,b,paint);canvas.drawRoundRect(x-dp(6),middle-dp(22),x+dp(6),middle+dp(22),dp(6),dp(6),paint);
                }
                paint.setTextSize(dp(13));paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("左 "+leftPercent+"%",l+(r-l)*leftPercent/100f,keysTop+dp(16),paint);
                canvas.drawText("右 "+rightPercent+"%",r-(r-l)*rightPercent/100f,keysTop+dp(16),paint);
                return;
            }
            for(float x:new float[]{l,(l+r)/2,r})for(float y:new float[]{t,(t+b)/2,b}){
                if(x==(l+r)/2&&y==(t+b)/2)continue;
                canvas.drawRoundRect(Math.max(0,x-dp(5)),Math.max(0,y-dp(5)),Math.min(getWidth(),x+dp(5)),Math.min(getHeight(),y+dp(5)),dp(2),dp(2),paint);
            }
            paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(12));
            String space="下の余白 "+Math.round((areaHeight-b)/density)+"dp";
            canvas.drawText(space,getWidth()/2f,Math.min(getHeight()-dp(2),b+dp(18)),paint);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            if(bounds==null)return true;
            int action=event.getActionMasked();
            if(!adjustingSides)scaleDetector.onTouchEvent(event);
            if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP){pointer=-1;return true;}
            if(pinching&&action!=MotionEvent.ACTION_UP&&action!=MotionEvent.ACTION_CANCEL)return true;
            if(action==MotionEvent.ACTION_DOWN){
                downX=event.getX();downY=event.getY();initial=bounds;edges=0;pointer=-1;float hit=dp(24);
                draggingSide=0;initialLeft=leftPercent;initialRight=rightPercent;
                if(downX<bounds.left()-hit||downX>bounds.right()+hit||downY<bounds.top()-hit||downY>bounds.bottom()+hit)return true;
                pointer=event.getPointerId(0);
                if(adjustingSides){
                    float leftDistance=Math.abs(downX-(bounds.left()+bounds.width()*leftPercent/100f)),rightDistance=Math.abs(downX-(bounds.right()-bounds.width()*rightPercent/100f));
                    if(Math.min(leftDistance,rightDistance)<=hit)draggingSide=leftDistance<=rightDistance?-1:1;
                    getParent().requestDisallowInterceptTouchEvent(true);return true;
                }
                if(Math.abs(downX-bounds.left())<hit)edges|=KeyboardGeometry.LEFT;else if(Math.abs(downX-bounds.right())<hit)edges|=KeyboardGeometry.RIGHT;
                if(Math.abs(downY-bounds.top())<hit)edges|=KeyboardGeometry.TOP;else if(Math.abs(downY-bounds.bottom())<hit)edges|=KeyboardGeometry.BOTTOM;
                getParent().requestDisallowInterceptTouchEvent(true);return true;
            }
            int index=event.findPointerIndex(pointer);
            if(adjustingSides){
                if(action==MotionEvent.ACTION_MOVE&&index>=0&&draggingSide!=0){float x=event.getX(index);if(draggingSide<0)setSideWidthPercents(Math.round((x-bounds.left())/bounds.width()*100),rightPercent);else setSideWidthPercents(leftPercent,Math.round((bounds.right()-x)/bounds.width()*100));}
                if(action==MotionEvent.ACTION_CANCEL)setSideWidthPercents(initialLeft,initialRight);
                if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){pointer=-1;draggingSide=0;performClick();}return true;
            }
            if(action==MotionEvent.ACTION_MOVE&&index>=0){bounds=KeyboardGeometry.drag(initial,edges,event.getX(index)-downX,event.getY(index)-downY,getWidth(),areaHeight,minWidth(),minHeight(),dp(12));KeyboardDockView.this.requestLayout();invalidate();}
            if(action==MotionEvent.ACTION_CANCEL){if(initial!=null)bounds=initial;pointer=-1;pinching=false;KeyboardDockView.this.requestLayout();}
            if(action==MotionEvent.ACTION_UP){pointer=-1;pinching=false;performClick();}
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
