package net.neomenia.inputcore;

/** Tap cycle: single-use Shift, CapsLock, off. */
public final class ShiftState {
    private boolean shifted=false,locked=false;
    public boolean upper(){return shifted||locked;}
    public boolean locked(){return locked;}
    public void reset(){shifted=false;locked=false;}
    public void tap(){
        if(locked){locked=false;shifted=false;}
        else if(shifted){locked=true;shifted=false;}
        else shifted=true;
    }
    public void typed(String raw){if(raw.codePoints().anyMatch(Character::isLetter))shifted=false;}
}
