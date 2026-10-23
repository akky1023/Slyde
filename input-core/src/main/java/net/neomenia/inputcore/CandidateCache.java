package net.neomenia.inputcore;

import java.util.*;

/** Bounded reading cache shared by the UI lookup and the dictionary worker. */
public final class CandidateCache {
    private final int capacity;
    private final LinkedHashMap<String,List<String>> entries=new LinkedHashMap<>(16,.75f,true);
    public CandidateCache(int capacity){if(capacity<1)throw new IllegalArgumentException("capacity");this.capacity=capacity;}
    public synchronized List<String> get(String reading){return entries.get(reading);}
    public synchronized void put(String reading,List<String> values){
        entries.put(reading,Collections.unmodifiableList(new ArrayList<>(values)));
        if(entries.size()>capacity)entries.remove(entries.keySet().iterator().next());
    }
}
