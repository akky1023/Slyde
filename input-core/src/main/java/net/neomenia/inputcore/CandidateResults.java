package net.neomenia.inputcore;

import java.util.*;

/** Results may arrive in either order; neither source erases the other. UI-thread confined. */
public final class CandidateResults {
    private List<String> dictionary;
    private String ai;
    private boolean dictionaryReady;
    public CandidateResults(List<String> initial){this(initial,false);}
    public CandidateResults(List<String> initial,boolean ready){dictionary=new ArrayList<>(initial);dictionaryReady=ready;}
    public void dictionary(List<String> values){dictionary(values,true);}
    public void dictionary(List<String> values,boolean ready){dictionary=new ArrayList<>(values);dictionaryReady=ready;}
    public void ai(String value){if(ZenzCandidates.safe(value))ai=value;}
    public String ai(){return ai;}
    private static boolean historicalKana(String value){
        // Includes archaic kana also filed under へんたいがな in the bundled Mozc dictionary.
        return value.codePoints().anyMatch(cp->cp>=0x1b000&&cp<=0x1b122);
    }
    public List<String> values(){
        List<String> merged=ZenzCandidates.merge(dictionary,ai,dictionaryReady);
        List<String> regular=new ArrayList<>(),historical=new ArrayList<>();
        for(String value:merged)(historicalKana(value)?historical:regular).add(value);
        regular.addAll(historical);return regular;
    }
}
