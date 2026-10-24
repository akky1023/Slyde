package net.neomenia.inputcore;

import java.util.ArrayList;
import java.util.List;

/** Retains the entered roman spelling alongside streaming kana, including confirmed NN. */
public final class JapaneseComposition {
    private record Part(String kana,String raw) {}
    private final List<Part> parts=new ArrayList<>();
    private String kana="";
    public int length(){return kana.length();}
    @Override public String toString(){return kana;}
    public void clear(){parts.clear();kana="";}
    public String alphabet(){
        StringBuilder raw=new StringBuilder();for(Part part:parts)raw.append(part.raw());
        // The decoder uses n' to finish a glide ending in N; the apostrophe is internal.
        return raw.toString().replace("n'","n");
    }
    public void append(String raw){
        if(raw.isEmpty())return;
        String next=Romanizer.append(kana,raw);
        int common=0;while(common<kana.length()&&common<next.length()&&kana.charAt(common)==next.charAt(common))common++;
        int keep=0,boundary=0;
        while(keep<parts.size()&&boundary+parts.get(keep).kana().length()<=common){boundary+=parts.get(keep++).kana().length();}
        StringBuilder merged=new StringBuilder();for(int i=keep;i<parts.size();i++)merged.append(parts.get(i).raw());
        merged.append(raw);parts.subList(keep,parts.size()).clear();
        parts.add(new Part(next.substring(boundary),merged.toString()));kana=next;
    }
    public void delete(){
        if(kana.isEmpty())return;
        Part last=parts.remove(parts.size()-1);
        int end=last.kana().offsetByCodePoints(last.kana().length(),-1);
        String remainder=last.kana().substring(0,end);
        kana=kana.substring(0,kana.length()-last.kana().length());
        if(!remainder.isEmpty()){
            String raw=null;
            for(int size=last.raw().length();size>0;size--){
                String prefix=last.raw().substring(0,size);
                if(Romanizer.append(kana,prefix).equals(kana+remainder)){raw=prefix;break;}
            }
            if(raw==null)raw=Romanizer.romanSpelling(remainder);
            parts.add(new Part(remainder,raw));kana+=remainder;
        }
    }
}
