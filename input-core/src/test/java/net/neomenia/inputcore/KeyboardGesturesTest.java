package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class KeyboardGesturesTest {
    private static GlideDecoder.Point p(float x,float y){return new GlideDecoder.Point(x,y);}
    @Test public void centerUpFlickIsRecognizedWithSmallFingerDrift(){
        assertTrue(KeyboardGestures.isNumberToggle(List.of(p(1.5f,1.5f),p(1.55f,1.1f),p(1.6f,.5f))));
        assertTrue(KeyboardGestures.isNumberToggle(List.of(p(1.2f,1.8f),p(1.15f,.7f))));
    }
    @Test public void tapsAndOtherDirectionsAreNotModeSwitches(){
        assertFalse(KeyboardGestures.isNumberToggle(List.of(p(1.5f,1.5f),p(1.5f,1.48f))));
        assertFalse(KeyboardGestures.isNumberToggle(List.of(p(1.5f,1.5f),p(.5f,.5f))));
        assertFalse(KeyboardGestures.isNumberToggle(List.of(p(1.5f,.5f),p(1.5f,1.5f))));
    }
    @Test public void passingKOnTheWayToAnotherKeyDoesNotSwitch(){
        assertFalse(KeyboardGestures.isNumberToggle(List.of(p(1.5f,1.5f),p(1.5f,.5f),p(.5f,.5f))));
        assertFalse(KeyboardGestures.isNumberToggle(List.of(p(1.5f,1.5f),p(.5f,1.5f),p(1.5f,.5f))));
    }
    @Test public void upwardFlickCanFinishBeyondKeyboardInCandidateArea(){
        assertTrue(KeyboardGestures.isNumberToggle(List.of(p(1.5f,1.5f),p(1.48f,.5f),p(1.45f,-.8f))));
        assertTrue(KeyboardGestures.isNumberToggle(List.of(p(1.5f,1.5f),p(1.5f,-2))));
        assertFalse(KeyboardGestures.isNumberToggle(List.of(p(1.5f,1.5f),p(2.5f,-.5f))));
        assertFalse(KeyboardGestures.isNumberToggle(List.of(p(1.5f,1.5f),p(1.5f,-.5f),p(1.5f,1.4f))));
    }
}
