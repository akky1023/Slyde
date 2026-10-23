package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyboardSizingTest {
    private static void repeatedMeasurement(int rows,int gap,boolean editing){
        int candidates=44*rows,savedHeight=candidates+232,controls=68;
        int parent=editing?600:savedHeight+gap;
        KeyboardGeometry.Rect bounds=new KeyboardGeometry.Rect(0,0,360,savedHeight);
        for(int pass=0;pass<40;pass++){
            int canvas=KeyboardSizing.canvasHeight(parent,candidates+96+12,controls,editing);
            bounds=KeyboardGeometry.fit(new KeyboardGeometry.Rect(0,canvas-gap-savedHeight,360,bounds.height()),360,canvas,240,candidates+128,12);
            int measured=(editing?canvas+controls:Math.round(canvas-bounds.top()));
            assertEquals("saved keyboard height at pass "+pass,savedHeight,bounds.height(),0);
            assertEquals("toolbar cannot shrink each measurement pass",parent,measured);
            parent=measured;
        }
    }
    @Test public void normalImeSecondMeasureDoesNotSubtractHiddenToolbar(){repeatedMeasurement(1,24,false);}
    @Test public void twoRowsMatchReportedBrowserCase(){repeatedMeasurement(2,72,false);}
    @Test public void threeCandidateRowsPreserveKeyHeight(){repeatedMeasurement(3,96,false);}
    @Test public void editControlsReservedExactlyOnce(){repeatedMeasurement(2,72,true);}
    @Test public void changingFromEditorToNormalKeepsSavedHeight(){
        int savedHeight=320,gap=72;
        int normal=KeyboardSizing.canvasHeight(savedHeight+gap,184,68,false);
        assertEquals(savedHeight+gap,normal);
        assertEquals(savedHeight+gap,KeyboardSizing.canvasHeight(normal,184,68,false));
        assertEquals(600-68,KeyboardSizing.canvasHeight(600,184,68,true));
    }
}
