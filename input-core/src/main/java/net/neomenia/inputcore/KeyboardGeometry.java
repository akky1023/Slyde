package net.neomenia.inputcore;

/** Crop-style keyboard geometry. All coordinates share the caller's unit. */
public final class KeyboardGeometry {
    public static final int LEFT=1,TOP=2,RIGHT=4,BOTTOM=8;
    public record Rect(float left,float top,float width,float height){
        public float right(){return left+width;}
        public float bottom(){return top+height;}
    }
    private KeyboardGeometry(){}
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
    public static Rect fit(Rect r,float width,float height,float minWidth,float minHeight,float gap){
        float w=clamp(r.width(),Math.min(minWidth,width),width),h=clamp(r.height(),Math.min(minHeight,height-gap),height-gap);
        return new Rect(clamp(r.left(),0,width-w),clamp(r.top(),0,height-gap-h),w,h);
    }
    public static Rect drag(Rect r,int edges,float dx,float dy,float width,float height,float minWidth,float minHeight,float gap){
        r=fit(r,width,height,minWidth,minHeight,gap);
        if(edges==0)return fit(new Rect(r.left()+dx,r.top()+dy,r.width(),r.height()),width,height,minWidth,minHeight,gap);
        minWidth=Math.min(minWidth,width);minHeight=Math.min(minHeight,height-gap);
        float l=r.left(),t=r.top(),right=r.right(),bottom=r.bottom();
        if((edges&LEFT)!=0)l=clamp(l+dx,0,right-minWidth);
        if((edges&RIGHT)!=0)right=clamp(right+dx,l+minWidth,width);
        if((edges&TOP)!=0)t=clamp(t+dy,0,bottom-minHeight);
        if((edges&BOTTOM)!=0)bottom=clamp(bottom+dy,t+minHeight,height-gap);
        return new Rect(l,t,right-l,bottom-t);
    }
    public static Rect scale(Rect r,float factor,float width,float height,float minWidth,float minHeight,float gap){
        if(!Float.isFinite(factor)||factor<=0)return fit(r,width,height,minWidth,minHeight,gap);
        float w=clamp(r.width()*factor,Math.min(minWidth,width),width);
        float h=clamp(r.height()*factor,Math.min(minHeight,height-gap),height-gap);
        return fit(new Rect(r.left()+(r.width()-w)/2,r.top()+(r.height()-h)/2,w,h),width,height,minWidth,minHeight,gap);
    }
}
