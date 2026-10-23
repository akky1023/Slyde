package net.neomenia.inputcore;

import java.util.List;

/** S->K and 5->2 share the same two center-column cells. Coordinates are key-relative. */
public final class KeyboardGestures {
    private KeyboardGestures() {}
    public static boolean isNumberToggle(List<GlideDecoder.Point> points){
        if(points.size()<2)return false;
        GlideDecoder.Point start=points.get(0),end=points.get(points.size()-1);
        // The gesture may continue above the keyboard into the candidate area.
        // A touch that began in S/5 remains owned by the keyboard until UP/CANCEL.
        if(KeyLayout.index(start.x(),start.y())!=4||end.y()>=1)return false;
        float up=start.y()-end.y(),side=Math.abs(start.x()-end.x());
        if(up<.45f||up<side*1.5f)return false;
        for(GlideDecoder.Point p:points){
            if(p.x()<1||p.x()>=2||Math.abs(p.x()-start.x())>.55f||p.y()>start.y()+.2f)return false;
        }
        return true;
    }
}
