package net.neomenia.glideprototype;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import net.neomenia.inputcore.*;
import java.util.*;

public final class GlideKeyboardView extends View {
    public interface Listener {
        void text(String raw, boolean japanese);
        void action(String action);
        void preview(String route, String result);
    }
    private final Listener listener;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final OrderedTouches<Stroke> touches=new OrderedTouches<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Drawable cursorLeftIcon,settingsIcon;
    private final Drawable[] rightIcons;
    private final Drawable shiftIcon,shiftOnIcon,capsIcon;
    private final ShiftState shift=new ShiftState();
    private boolean japanese=true;
    private int page=0;
    private static final class Stroke {
        final int pointerId,startSide,startRow;
        final float downX,downY;
        final List<GlideDecoder.Point> points=new ArrayList<>();
        int active=-1;float x,y;boolean repeating;Runnable repeat;
        Stroke(int id,int side,int row,float x,float y){pointerId=id;startSide=side;startRow=row;downX=x;downY=y;}
    }
    private int rowDp=58;
    private int naNyaBias=50;
    private boolean enableEu=false,enableIu=false;
    private int leftSidePercent=15,rightSidePercent=15;
    private JapaneseTypography typography=new JapaneseTypography(false,false,false,false);
    private static final String[] NUM={"1","2","3","4","5","6","7","8","9","記号1","0","記号2"};
    private final String[][] symbols={SymbolBoards.defaults(0),SymbolBoards.defaults(1)};
    private final boolean[] returnToNumbers={true,true};

    public void setSymbolBoard(int board,String[] values,boolean returnAfterInput){
        symbols[board]=values.clone();returnToNumbers[board]=returnAfterInput;invalidate();
    }

    public GlideKeyboardView(Context context,Listener listener){super(context);this.listener=listener;
        settingsIcon=UiIcons.drawable(context,R.drawable.ic_more);
        cursorLeftIcon=UiIcons.drawable(context,R.drawable.ic_cursor_left);
        rightIcons=new Drawable[]{UiIcons.drawable(context,R.drawable.ic_delete),UiIcons.drawable(context,R.drawable.ic_cursor_right),UiIcons.drawable(context,R.drawable.ic_space),UiIcons.drawable(context,R.drawable.ic_enter)};
        shiftIcon=UiIcons.drawable(context,R.drawable.ic_shift);shiftOnIcon=UiIcons.drawable(context,R.drawable.ic_shift_on);capsIcon=UiIcons.drawable(context,R.drawable.ic_caps_lock_on);
        setFocusable(true);setContentDescription("アルテ配列のグライドキーボード");}
    public boolean isJapanese(){return japanese&&page==0;}
    public void setRowDp(int value){rowDp=value;requestLayout();}
    public void setNaNyaBias(int value){naNyaBias=Math.max(0,Math.min(100,value));}
    public void setVowelPairs(boolean eu,boolean iu){cancelTouches();enableEu=eu;enableIu=iu;}
    public void setTypography(JapaneseTypography value){typography=value;invalidate();}
    public void setTypeface(Typeface value){paint.setTypeface(value);invalidate();}
    public void setSideWidths(int leftPercent,int rightPercent){cancelTouches();leftSidePercent=Math.max(8,Math.min(25,leftPercent));rightSidePercent=Math.max(8,Math.min(25,rightPercent));invalidate();}
    private float density(){return getResources().getDisplayMetrics().density;}
    private float left(){return getWidth()*leftSidePercent/100f;}
    private float right(){return getWidth()*(100-rightSidePercent)/100f;}
    private float cw(){return (right()-left())/3;}
    private float rh(){return getHeight()/4f;}
    private String label(int index){String s=page==1?NUM[index]:page>=2?symbols[page-2][index]:KeyLayout.KEYS[index];return page==0?(japanese?s.toUpperCase(Locale.ROOT):caseText(s)):page>=2&&s.isEmpty()?"戻る":s;}
    public void resetMode(boolean latin){cancelTouches();page=0;japanese=!latin;shift.reset();invalidate();}
    public void showNumbers(){cancelTouches();page=1;invalidate();}
    public void showLetters(){cancelTouches();page=0;invalidate();}
    @Override protected void onMeasure(int width,int height){setMeasuredDimension(MeasureSpec.getSize(width),MeasureSpec.getMode(height)==MeasureSpec.EXACTLY?MeasureSpec.getSize(height):Math.round(rowDp*4*density()));}
    private void text(Canvas canvas,String value,float x,float y,float size,int color){paint.setColor(color);paint.setTextSize(size*density());paint.setTextAlign(Paint.Align.CENTER);ImeFonts.drawText(getContext(),canvas,value,x,y-(paint.ascent()+paint.descent())/2,paint);}
    private void icon(Canvas canvas,Drawable drawable,float x,float y,int color){
        float sideWidth=x<left()?left():getWidth()-right();
        float half=Math.min(13*density(),Math.min(sideWidth,rh())*.3f);drawable.setTint(color);
        drawable.setBounds(Math.round(x-half),Math.round(y-half),Math.round(x+half),Math.round(y+half));drawable.draw(canvas);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(Color.rgb(21,30,39));
        for(int index=0;index<12;index++){
            int row=index/3,col=index%3;float x=left()+col*cw(),y=row*rh();
            paint.setColor(isActive(index)?Color.rgb(43,106,112):Color.rgb(34,47,59));
            canvas.drawRoundRect(x+2*density(),y+2*density(),x+cw()-2*density(),y+rh()-2*density(),8*density(),8*density(),paint);
            text(canvas,label(index),x+cw()/2,y+rh()/2,(page==1&&(index==9||index==11))||(page>=2&&symbols[page-2][index].isEmpty())?18:29,page==0&&!japanese?Color.rgb(104,181,255):Color.WHITE);
            if(page==0){
                String base=KeyLayout.KEYS[index];
                for(int dir=0;dir<4;dir++){
                    String alt=KeyLayout.variant(base,dir,!japanese);if(japanese)alt=typography.format(alt);if(alt.isEmpty())continue;
                    float ax=x+cw()/2,ay=y+rh()/2;
                    if(dir==0)ax=x+cw()*.12f;if(dir==1)ay=y+rh()*.14f;if(dir==2)ax=x+cw()*.88f;if(dir==3)ay=y+rh()*.86f;
                    text(canvas,japanese?alt.toUpperCase(Locale.ROOT):caseText(alt),ax,ay,10,!japanese?Color.rgb(104,181,255):Color.rgb(160,191,207));
                }
            }
        }
        for(int row=0;row<4;row++){
            int sideColor=page==0&&!japanese?Color.rgb(104,181,255):Color.rgb(192,218,222);
            if(row==0&&page==0&&!japanese){
                icon(canvas,shift.locked()?capsIcon:shift.upper()?shiftOnIcon:shiftIcon,left()/2,(row+.5f)*rh(),sideColor);
            }
            else if(row==0)icon(canvas,settingsIcon,left()/2,(row+.5f)*rh(),sideColor);
            else if(row==1)icon(canvas,cursorLeftIcon,left()/2,(row+.5f)*rh(),sideColor);
            else if(row==2){
                float size=22;paint.setTextSize(size*density());float width=paint.measureText("あa");
                if(width>0)size*=Math.min(1,Math.max(1,left()-4*density())/width);
                text(canvas,"あa",left()/2,3*rh(),size,sideColor);
            }
            icon(canvas,rightIcons[row],(right()+getWidth())/2,(row+.5f)*rh(),sideColor);
            if(row==2&&isJapanese())text(canvas,"全角",(right()+getWidth())/2,(row+.15f)*rh(),9,sideColor);
        }
        for(Stroke stroke:touches.active())if(stroke.points.size()>1){
            Path path=new Path();boolean first=true;
            for(GlideDecoder.Point p:stroke.points){float x=left()+p.x()*cw(),y=p.y()*rh();if(first){path.moveTo(x,y);first=false;}else path.lineTo(x,y);}
            paint.setColor(Color.rgb(112,222,208));paint.setStrokeWidth(2*density());paint.setStyle(Paint.Style.STROKE);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        }
    }
    private boolean isActive(int index){for(Stroke stroke:touches.active())if(stroke.active==index)return true;return false;}
    private void add(Stroke stroke,float x,float y){stroke.x=x;stroke.y=y;stroke.points.add(new GlideDecoder.Point((x-left())/cw(),y/rh()));stroke.active=KeyLayout.index((x-left())/cw(),y/rh());}
    private void updatePreview(Stroke stroke){
        if(stroke.points.isEmpty()||stroke.startSide!=0)return;
        if((page==0||page==1)&&KeyboardGestures.isNumberToggle(stroke.points)){
            listener.preview(page==0?"S → K":"5 → 2",page==0?"数字キーボードへ":"文字キーボードへ");return;
        }
        if(page!=0)return;
        GlideDecoder.Result r=GlideDecoder.decode(stroke.points,!japanese,naNyaBias,enableEu,enableIu);listener.preview(r.route(),japanese?typography.format(Romanizer.convert(r.raw(),r.raw().length()>1)):caseText(r.raw()));
    }
    void cancelTouches(){handler.removeCallbacksAndMessages(null);touches.clear();invalidate();}
    private void samplePointers(MotionEvent event){
        for(int index=0;index<event.getPointerCount();index++){
            Stroke stroke=touches.get(event.getPointerId(index));if(stroke==null)continue;
            for(int h=0;h<event.getHistorySize();h++)add(stroke,event.getHistoricalX(index,h),event.getHistoricalY(index,h));
            add(stroke,event.getX(index),event.getY(index));
            if(Math.hypot(stroke.x-stroke.downX,stroke.y-stroke.downY)>(isSettings(stroke)?ViewConfiguration.get(getContext()).getScaledTouchSlop():20*density())&&stroke.repeat!=null)handler.removeCallbacks(stroke.repeat);
        }
    }
    private boolean isSettings(Stroke stroke){return stroke.startSide==-1&&stroke.startRow==0&&(page!=0||japanese);}
    private String caseText(String s){return shift.upper()?s.toUpperCase(Locale.ROOT):s;}
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked(),index=event.getActionIndex();
        if(action==MotionEvent.ACTION_CANCEL){cancelTouches();listener.preview("","");return true;}
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            if(action==MotionEvent.ACTION_DOWN){cancelTouches();getParent().requestDisallowInterceptTouchEvent(true);}
            samplePointers(event);
            float x=event.getX(index),y=event.getY(index);
            Stroke stroke=new Stroke(event.getPointerId(index),x<left()?-1:x>=right()?1:0,(x<left()&&y>=2*rh())?3:Math.min(3,Math.max(0,(int)(y/rh()))),x,y);
            // Once a second finger joins, deletion is committed in the same press order as text.
            for(Stroke held:touches.active())if(held.repeat!=null)handler.removeCallbacks(held.repeat);
            if(touches.start(stroke.pointerId,stroke)){
                add(stroke,x,y);
                if(touches.active().size()==1&&stroke.startSide==1&&stroke.startRow==0){
                    stroke.repeat=new Runnable(){public void run(){
                        if(touches.get(stroke.pointerId)==stroke&&touches.isFirst(stroke)&&touches.active().size()==1){listener.action("delete");stroke.repeating=true;handler.postDelayed(this,65);}
                    }};handler.postDelayed(stroke.repeat,380);
                }
                if(touches.active().size()==1&&isSettings(stroke)){
                    stroke.repeat=()->{
                        if(touches.get(stroke.pointerId)==stroke&&touches.active().size()==1){
                            cancelTouches();performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);listener.action("settings");
                        }
                    };
                    handler.postDelayed(stroke.repeat,ViewConfiguration.getLongPressTimeout());
                }
                updatePreview(stroke);
            }
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            samplePointers(event);for(Stroke stroke:touches.active())updatePreview(stroke);invalidate();return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){
            samplePointers(event);
            Stroke stroke=touches.get(event.getPointerId(index));if(stroke!=null&&stroke.repeat!=null)handler.removeCallbacks(stroke.repeat);
            for(Stroke ready:touches.finish(event.getPointerId(index))){
                int previousPage=page;boolean previousJapanese=japanese,previousUpper=shift.upper(),previousCaps=shift.locked();
                if(!ready.repeating){emit(ready);performClick();}
                // A mode change ends any older touches on the previous layout.
                if(page!=previousPage||japanese!=previousJapanese||(ready.startSide==-1&&ready.startRow==0&&(shift.upper()!=previousUpper||shift.locked()!=previousCaps))){cancelTouches();break;}
            }
            invalidate();return true;
        }
        return true;
    }
    private void emit(Stroke stroke){
        if(isSettings(stroke)){
            if(isJapanese()&&Math.hypot(stroke.x-stroke.downX,stroke.y-stroke.downY)<=ViewConfiguration.get(getContext()).getScaledTouchSlop())listener.action("alphabet");
            return;
        }
        if(stroke.startSide==-1&&stroke.startRow==3){
            if(Math.hypot(stroke.x-stroke.downX,stroke.y-stroke.downY)<.5f*cw())sideAction(-1,3);
            return;
        }
        if(stroke.startSide!=0){
            float dx=stroke.x-stroke.downX,dy=stroke.y-stroke.downY;
            if(isJapanese()&&stroke.startSide==1&&stroke.startRow==2&&dy< -20*density()&&-dy>Math.abs(dx)){listener.text("　",false);listener.preview("空白 ↑","全角空白");}
            else if(japanese&&page==0&&stroke.active>=0&&Math.abs(dx)>.22f*cw())listener.text("ltu",true);
            else if(Math.hypot(stroke.x-stroke.downX,stroke.y-stroke.downY)<.5f*cw())sideAction(stroke.startSide,stroke.startRow);
        }else if((page==0||page==1)&&KeyboardGestures.isNumberToggle(stroke.points)){
            listener.action("commit");page=page==0?1:0;
            listener.preview(page==1?"S → K":"5 → 2",page==1?"数字キーボード":"文字キーボード");
        }else if(page==0){
            GlideDecoder.Result r=GlideDecoder.decode(stroke.points,!japanese,naNyaBias,enableEu,enableIu);String raw=r.raw();
            if(japanese&&raw.length()>1&&raw.endsWith("n"))raw+="'";
            if(!raw.isEmpty())listener.text(japanese?raw:caseText(raw),japanese);
            listener.preview(r.route(),japanese?typography.format(Romanizer.convert(raw,true)):caseText(raw));
            if(!japanese&&!raw.isEmpty())shift.typed(raw);
        }else if(stroke.active>=0){
            if(page==1&&(stroke.active==9||stroke.active==11)){
                page=stroke.active==9?2:3;listener.preview("記号"+(page-1),"記号を選んで入力");
            }else{
                String value=page>=2?symbols[page-2][stroke.active]:label(stroke.active);
                if(value.isEmpty()){page=1;listener.preview("戻る","数字キーボード");}
                else{listener.text(value,false);listener.preview(value,value);if(page>=2&&returnToNumbers[page-2])page=1;}
            }
        }
    }
    private void sideAction(int startSide,int startRow){
        if(startSide==1){listener.action(new String[]{"delete","right","space","enter"}[startRow]);return;}
        switch(startRow){
            case 0 -> {if(page==0&&!japanese){shift.tap();listener.preview(shift.locked()?"CapsLock":shift.upper()?"Shift":"英字",shift.locked()?"大文字を固定":shift.upper()?"次の英字入力を大文字に":"小文字入力");}}
            case 1 -> listener.action("left");
            case 3 -> {listener.action("commit");if(page!=0)page=0;else japanese=!japanese;listener.preview(japanese?"日本語":"英字", "キー配列は同じです");}
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDetachedFromWindow(){cancelTouches();super.onDetachedFromWindow();}
}
