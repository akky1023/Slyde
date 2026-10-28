package net.neomenia.inputcore;

import java.util.*;

/** Keep overlapping touches in press order even when their release order differs. */
public final class OrderedTouches<T> {
    private static final class Entry<T> {
        final T value; boolean finished;
        Entry(T value){this.value=value;}
    }
    private final Map<Integer,Entry<T>> active=new LinkedHashMap<>();
    private final Deque<Entry<T>> pending=new ArrayDeque<>();
    public boolean start(int pointerId,T value){
        if(active.size()>=2||active.containsKey(pointerId))return false;
        Entry<T> entry=new Entry<>(value);active.put(pointerId,entry);pending.addLast(entry);return true;
    }
    public T get(int pointerId){Entry<T> entry=active.get(pointerId);return entry==null?null:entry.value;}
    public Collection<T> active(){List<T> values=new ArrayList<>();for(Entry<T> entry:active.values())values.add(entry.value);return values;}
    public boolean isFirst(T value){return !pending.isEmpty()&&pending.peekFirst().value==value;}
    public List<T> finish(int pointerId){
        Entry<T> entry=active.remove(pointerId);if(entry==null)return List.of();entry.finished=true;
        return drain();
    }
    /** Commit a held pair only when no other input is waiting between them. */
    public boolean commitHeldPair(int firstId,int secondId){
        Entry<T> first=active.get(firstId),second=active.get(secondId);
        if(first==null||second==null||first==second||pending.size()!=2||pending.peekFirst()!=first||pending.peekLast()!=second)return false;
        pending.clear();return true;
    }
    private List<T> drain(){
        List<T> ready=new ArrayList<>();
        while(!pending.isEmpty()&&pending.peekFirst().finished)ready.add(pending.removeFirst().value);
        return ready;
    }
    public void clear(){active.clear();pending.clear();}
}
