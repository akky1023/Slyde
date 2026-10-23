package net.neomenia.inputcore;

import java.util.*;

/** Decode deliberate bends/endpoints, rather than every key crossed by a straight line. */
public final class GlideDecoder {
    public record Point(float x,float y) {}
    public record Result(String raw, String route, List<Point> corners) {}
    public static Result decode(List<Point> samples, boolean english) {
        return decode(samples,english,50);
    }
    /** 0: prefer NA on ambiguous bends, 50: original behavior, 100: prefer NYA. */
    public static Result decode(List<Point> samples, boolean english, int naNyaBias) {
        return decode(samples,english,naNyaBias,true,true);
    }
    /** Ignore intermediate E/I in disabled consonant-led EU/IU; standalone vowels and flicks remain independent. */
    public static Result decode(List<Point> samples, boolean english, int naNyaBias, boolean enableEu, boolean enableIu) {
        if(samples.isEmpty()) return new Result("","",List.of());
        Point start=samples.get(0), end=samples.get(samples.size()-1);
        int first=KeyLayout.index(start.x,start.y);
        if(first<0) return new Result("","",List.of());
        String base=KeyLayout.key(first), selected=base;
        List<Point> corners=simplify(samples,0.14f);
        Point initial=corners.size()>1?corners.get(1):end;
        float dx=initial.x-start.x,dy=initial.y-start.y;
        int direction=Math.abs(dx)>Math.abs(dy)?(dx<0?0:2):(dy<0?1:3);
        String alternate=KeyLayout.variant(base,direction,english);
        String rightPrefix=initialRightFlick(samples,start,base);
        String specialPrefix=initialSpecialFlick(samples,start,base);
        boolean jPrefix=base.equals("r")&&hasUpwardJPrefix(samples,start);
        if(jPrefix)alternate="j";
        if(!rightPrefix.isEmpty())alternate=rightPrefix;
        if(!specialPrefix.isEmpty())alternate=specialPrefix;
        boolean shortFlick=Math.hypot(end.x-start.x,end.y-start.y)>.22 &&
            (KeyLayout.index(end.x,end.y)==first || KeyLayout.index(end.x,end.y)<0);
        boolean firstBendFlick=corners.size()>2 && Math.hypot(dx,dy)>.22 &&
            ((direction==0||direction==2)?Math.abs(dy)<.55:Math.abs(dx)<.55) &&
            Math.hypot(dx,dy)<1.15;
        // U/E/O lateral alternates take priority over standalone vowel pairs.
        String endKey=KeyLayout.key(KeyLayout.index(end.x,end.y));
        boolean punctuation=((base.equals("u")&&endKey.equals("e"))||(base.equals("e")&&(endKey.equals("o")||endKey.equals("u")))||(base.equals("o")&&endKey.equals("e")))
            && specialPrefix.isEmpty() && Math.abs(end.y-start.y)<.65 && Math.abs(end.x-start.x)>.35;
        boolean englishSymbol=english&&(base.equals("a")||base.equals("y"))&&direction==2&&end.x-start.x>.22&&Math.abs(end.y-start.y)<.65;
        boolean variant=(!alternate.isEmpty())&&(shortFlick||(firstBendFlick&&alternate.matches("[a-z]"))||punctuation||englishSymbol||jPrefix||!rightPrefix.isEmpty()||!specialPrefix.isEmpty());
        if(variant) selected=alternate;
        StringBuilder raw=new StringBuilder(selected); List<String> route=new ArrayList<>(); route.add(base.toUpperCase(Locale.ROOT));
        if(variant) route.add(selected.toUpperCase(Locale.ROOT));
        float travel=0;for(int i=1;i<samples.size();i++)travel+=Math.hypot(samples.get(i).x-samples.get(i-1).x,samples.get(i).y-samples.get(i-1).y);
        boolean returned=travel>Math.hypot(end.x-start.x,end.y-start.y)+.25;
        if(punctuation || englishSymbol || (shortFlick && !returned && (KeyLayout.index(end.x,end.y)==first || KeyLayout.index(end.x,end.y)<0)))
            return new Result(adapt(selected,english),String.join(" → ",route),corners);
        boolean hasConsonant=!KeyLayout.vowel(selected) && selected.matches("[a-z]");
        boolean ySeen=selected.equals("y"),hSeen=false; int vowels=KeyLayout.vowel(selected)?1:0;
        boolean small=selected.equals("x")||selected.equals("l");
        boolean singleVowel=selected.equals("q")||selected.equals("c")||small||selected.equals("v");
        // A detour through Y then O cannot be a direct A/I syllable. Preserve YO
        // before interpreting rounded bends near the intervening A/I keys.
        int yoTurn=-1;
        if(!singleVowel&&hasConsonant&&Romanizer.isSyllable(selected+"yo")){
            boolean visitedY=false;
            for(Point p:samples){
                if(p.x>=0&&p.x<1.1f&&p.y>.8f&&p.y<2.2f)visitedY=true;
                if(visitedY&&nearO(p)){
                    for(int i=1;i<corners.size();i++)if(nearO(corners.get(i))){yoTurn=i;break;}
                    break;
                }
            }
        }
        if(yoTurn>=0){raw.append('y');route.add("Y");ySeen=true;}
        int lastVowel=-1;
        if(singleVowel)for(int i=1;i<corners.size();i++){
            Point p=corners.get(i);if(KeyLayout.vowel(KeyLayout.key(KeyLayout.index(p.x,p.y))))lastVowel=i;
        }
        Point firstA=null;
        String last=variant?selected:base;
        for(int i=1;i<corners.size();i++) {
            Point p=corners.get(i); String key=KeyLayout.key(KeyLayout.index(p.x,p.y));
            if(yoTurn>=0&&i<yoTurn)continue;
            if(i==yoTurn)key="o";
            if(key.isEmpty()||key.equals(last)) continue;
            if(singleVowel&&KeyLayout.vowel(key)&&i!=lastVowel)continue;
            if(ySeen&&vowels==0&&KeyLayout.vowel(key)&&!Romanizer.isSyllable(raw+key)&&Romanizer.isSyllable(selected+key)){
                raw.setLength(selected.length());route.remove("Y");ySeen=false;
            }
            // Intermediate I/E bends are ignored before YA/YU/YO; endpoints
            // remain valid YI/YE syllables (e.g. T-Y-I-U -> TYU).
            if(ySeen&&vowels==0&&(key.equals("i")||key.equals("e"))&&i!=corners.size()-1)continue;
            if(vowels==0 && !KeyLayout.vowel(key) && !(hasConsonant&&!ySeen&&key.equals("y"))) {
                // Rounded turns may reach the adjacent row only after turning. Allow a
                // nearby legal vowel at a bend, but never infer arbitrary crossed consonants.
                float nearest=.72f;String snapped="";
                for(int k=0;k<KeyLayout.KEYS.length;k++){
                    String candidate=KeyLayout.KEYS[k];if(!KeyLayout.vowel(candidate))continue;
                    float distance=(float)Math.hypot(p.x-(k%3+.5f),p.y-(k/3+.5f));
                    if(distance<nearest){nearest=distance;snapped=candidate;}
                }
                if(!snapped.isEmpty())key=snapped;
            }
            boolean replaceVowel=hasConsonant&&!singleVowel&&vowels==1&&i==corners.size()-1&&key.equals("u")&&
                ((last.equals("e")&&!enableEu)||(last.equals("i")&&!enableIu));
            boolean accept=false;
            if(hasConsonant && vowels==0 && !ySeen && !hSeen && !small && key.equals("y")&&Romanizer.isSyllablePrefix(raw+"y")) {if(!selected.equals("f")){accept=true;ySeen=true;}}
            else if(vowels==0&&!ySeen&&!hSeen&&key.equals("h")&&Romanizer.isSyllablePrefix(raw+"h")){accept=true;hSeen=true;}
            else if(KeyLayout.vowel(key) &&
                ((vowels==0&&(!singleVowel||i==lastVowel)&&Romanizer.isSyllable(raw+key)) || (!singleVowel&&vowels==1&&i==corners.size()-1))) {accept=true;vowels++;}
            else if(key.equals("n") && vowels==1 && i==corners.size()-1) {accept=true;}
            if(accept) {
                if(replaceVowel){raw.setLength(raw.length()-1);route.remove(route.size()-1);vowels--;}
                if(hSeen&&(selected.equals("t")||selected.equals("d"))&&key.equals("a")){raw.setLength(1);route.remove("H");}
                if(ySeen&&key.equals("i")&&vowels==1&&!selected.equals("w")){raw.setLength(raw.length()-1);route.remove("Y");}
                raw.append(key);route.add(key.toUpperCase(Locale.ROOT));last=key;if(key.equals("a")&&vowels==1)firstA=p;
            }
        }
        int bias=Math.max(0,Math.min(100,naNyaBias));
        if(yoTurn<0&&selected.equals("n")&&firstA!=null&&bias!=50&&(raw.toString().startsWith("na")||raw.toString().startsWith("nya"))){
            boolean preferY=hasNyaBend(samples,start,firstA,bias);
            if(preferY&&raw.toString().startsWith("na")){raw.insert(1,'y');route.add(1,"Y");}
            else if(!preferY&&raw.toString().startsWith("nya")){raw.deleteCharAt(1);route.remove(1);}
        }
        return new Result(adapt(raw.toString(),english),String.join(" → ",route),corners);
    }
    private static boolean nearO(Point p){
        return p.y>2.8f&&p.x>1.8f&&Math.hypot(p.x-2.5f,p.y-3.5f)<.75f;
    }
    private static String initialSpecialFlick(List<Point> samples,Point start,String base){
        if(!base.equals("a")&&!base.equals("u")&&!base.equals("h"))return "";
        boolean upward=!base.equals("u");
        for(int i=1;i<samples.size();i++){
            Point p=samples.get(i);
            float along=upward?start.y-p.y:start.x-p.x;
            float across=upward?Math.abs(p.x-start.x):Math.abs(p.y-start.y);
            if(across>.35f||along<-.12f)return "";
            if(along>=.18f&&along>=across*1.6f)return switch(base){case "a"->"x";case "h"->"p";default->"v";};
        }
        return "";
    }
    private static boolean hasNyaBend(List<Point> samples,Point start,Point firstA,int bias){
        float dx=firstA.x-start.x,dy=firstA.y-start.y;
        float length=(float)Math.hypot(dx,dy);if(length==0)return false;
        float deviation=0;
        for(Point p:samples){
            if(KeyLayout.index(p.x,p.y)==3){
                float distance=Math.abs(dx*(p.y-start.y)-dy*(p.x-start.x))/length;
                deviation=Math.max(deviation,distance);
            }
            if(p==firstA)break;
        }
        float threshold=bias<50?.38f-(bias/50f)*.24f:.14f-((bias-50)/50f)*.105f;
        return deviation>=threshold;
    }
    private static boolean hasUpwardJPrefix(List<Point> samples,Point start){
        for(int i=1;i<samples.size();i++){
            Point p=samples.get(i);float sideways=Math.abs(p.x-start.x),up=start.y-p.y;
            if(sideways>.45f||p.y>start.y+.15f)return false;
            if(up>.22f&&up>sideways*2)return true;
        }
        return false;
    }
    private static String initialRightFlick(List<Point> samples,Point start,String base){
        String alternate=switch(base){case "k"->"g";case "s"->"z";case "n"->"m";default->"";};
        if(alternate.isEmpty())return "";
        for(int i=1;i<samples.size();i++){
            Point p=samples.get(i);float right=p.x-start.x,vertical=Math.abs(p.y-start.y);
            if(vertical>.35f||right<-.12f)return "";
            if(right>=.18f&&right>=vertical*1.6f)return alternate;
        }
        return "";
    }
    private static String adapt(String text,boolean english) {
        return english?text.replace('。','.').replace('、',',').replace('！','!').replace('？','?').replace('ー','-').replace('（','(').replace('）',')').replace('～','~'):text;
    }
    public static List<Point> simplify(List<Point> points,float epsilon) {
        if(points.size()<3) return new ArrayList<>(points);
        boolean[] keep=new boolean[points.size()];keep[0]=true;keep[points.size()-1]=true;
        split(points,0,points.size()-1,epsilon,keep);
        List<Point> result=new ArrayList<>(); for(int i=0;i<points.size();i++) if(keep[i]) result.add(points.get(i));return result;
    }
    private static void split(List<Point> p,int a,int b,float eps,boolean[] keep) {
        Point s=p.get(a),e=p.get(b);float dx=e.x-s.x,dy=e.y-s.y,den=dx*dx+dy*dy,max=0;int index=-1;
        for(int i=a+1;i<b;i++) {Point q=p.get(i);float t=den==0?0:Math.max(0,Math.min(1,((q.x-s.x)*dx+(q.y-s.y)*dy)/den));float dist=(float)Math.hypot(q.x-s.x-t*dx,q.y-s.y-t*dy);if(dist>max){max=dist;index=i;}}
        if(index>=0&&max>eps) {keep[index]=true;split(p,a,index,eps,keep);split(p,index,b,eps,keep);}
    }
}
