package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyboardGeometryTest {
    private final KeyboardGeometry.Rect start=new KeyboardGeometry.Rect(60,100,240,260);
    private KeyboardGeometry.Rect drag(int edge,float x,float y){return KeyboardGeometry.drag(start,edge,x,y,400,500,220,180,12);}
    @Test public void movePreservesSizeAndClampsToBottomGap(){
        var r=drag(0,999,999);assertEquals(160,r.left(),0);assertEquals(488,r.bottom(),0);assertEquals(240,r.width(),0);assertEquals(260,r.height(),0);
    }
    @Test public void cornerResizeKeepsOppositeCorner(){
        var r=drag(KeyboardGeometry.LEFT|KeyboardGeometry.TOP,20,30);assertEquals(300,r.right(),0);assertEquals(360,r.bottom(),0);assertEquals(220,r.width(),0);assertEquals(230,r.height(),0);
    }
    @Test public void edgesCannotCrossOrLeaveScreen(){
        var r=drag(KeyboardGeometry.RIGHT|KeyboardGeometry.BOTTOM,-999,-999);assertEquals(220,r.width(),0);assertEquals(180,r.height(),0);
        r=drag(KeyboardGeometry.LEFT|KeyboardGeometry.TOP,-999,-999);assertEquals(0,r.left(),0);assertEquals(0,r.top(),0);
    }
    @Test public void bottomResizeAlwaysLeavesGap(){assertEquals(488,drag(KeyboardGeometry.BOTTOM,0,999).bottom(),0);}
    @Test public void narrowLandscapeStillFits(){
        var r=KeyboardGeometry.fit(start,200,190,240,220,12);assertEquals(200,r.width(),0);assertEquals(178,r.height(),0);assertEquals(178,r.bottom(),0);
    }
    @Test public void pinchScalesAroundCenterAndCanBeReversed(){
        var large=KeyboardGeometry.scale(start,1.1f,400,500,100,100,12);
        assertEquals(264,large.width(),.001f);assertEquals(286,large.height(),.001f);
        assertEquals(180,large.left()+large.width()/2,.001f);assertEquals(230,large.top()+large.height()/2,.001f);
        var restored=KeyboardGeometry.scale(large,1/1.1f,400,500,100,100,12);
        assertEquals(start.left(),restored.left(),.001f);assertEquals(start.top(),restored.top(),.001f);
    }
    @Test public void pinchHonorsMinimumSizeScreenAndBottomGap(){
        var small=KeyboardGeometry.scale(start,.01f,400,500,220,180,12);
        assertEquals(220,small.width(),0);assertEquals(180,small.height(),0);
        var large=KeyboardGeometry.scale(start,100,400,500,220,180,12);
        assertEquals(400,large.width(),0);assertEquals(488,large.height(),0);assertEquals(488,large.bottom(),0);
        assertEquals(start,KeyboardGeometry.scale(start,Float.NaN,400,500,220,180,12));
    }
}
