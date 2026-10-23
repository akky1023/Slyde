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
        List<T> ready=new ArrayList<>();
        while(!pending.isEmpty()&&pending.peekFirst().finished)ready.add(pending.removeFirst().value);
        return ready;
    }
    public void clear(){active.clear();pending.clear();}
}
