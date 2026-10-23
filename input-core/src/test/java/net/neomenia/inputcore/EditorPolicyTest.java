package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;

public class EditorPolicyTest {
    @Test public void passwordsIncludeWebVisibleAndNumeric(){
        for(int type:new int[]{0x81,0x91,0xe1,0x12}){
            assertTrue(EditorPolicy.isPassword(type));assertTrue(EditorPolicy.prefersLatin(type));
        }
        assertFalse(EditorPolicy.isPassword(0x20001));
        assertFalse(EditorPolicy.isPassword(0x21));
    }
    @Test public void emailAndUrlStartInLatin(){
        for(int type:new int[]{0x11,0x21,0xd1})assertTrue(EditorPolicy.prefersLatin(type));
        assertFalse(EditorPolicy.prefersLatin(0x20001));
    }
    @Test public void flagsDoNotChangeNumericLayout(){
        for(int type:new int[]{2,3,4,0x1002,0x2002,0x12})assertTrue(EditorPolicy.prefersNumbers(type));
        assertFalse(EditorPolicy.prefersNumbers(1));
    }
    @Test public void changingFieldsClearsOneShotAndCapsLock(){
        ShiftState shift=new ShiftState();shift.tap();shift.reset();assertFalse(shift.upper());
        shift.tap();shift.tap();assertTrue(shift.locked());shift.reset();assertFalse(shift.locked());assertFalse(shift.upper());
    }
}
