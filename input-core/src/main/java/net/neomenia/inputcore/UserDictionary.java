package net.neomenia.inputcore;

import java.io.*;
import java.text.Normalizer;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/** Mozc text interchange (reading, word, part of speech, optional comment). */
public final class UserDictionary {
    public static final int MAX_ENTRIES=100000;
    public record Entry(String reading,String word,String partOfSpeech,String comment) {
        public Entry {
            reading=normalize(reading);
            if(partOfSpeech.equals("除外"))partOfSpeech="抑制単語";
            boolean policy=partOfSpeech.equals("抑制単語")||partOfSpeech.equals("除外")||partOfSpeech.equals("抑制");
            if(partOfSpeech.isBlank()||(!policy&&(reading.isBlank()||word.isBlank()))||(policy&&reading.isBlank()&&word.isBlank()))throw new IllegalArgumentException(policy?"よみ・単語のどちらかを入力してください":"よみ・単語・品詞を入力してください");
            for(String field:List.of(reading,word,partOfSpeech,comment)){
                if(field.length()>1024||field.codePoints().anyMatch(cp->Character.isISOControl(cp)))
                    throw new IllegalArgumentException("各項目は1024文字以内で、改行・タブを含めずに入力してください");
            }
        }
    }
    private static final class Node {
        final Map<Character,Node> children=new HashMap<>();
        final LinkedHashSet<String> words=new LinkedHashSet<>();
    }
    private final List<Entry> entries;
    private final Node root=new Node();
    private final Map<String,Set<String>> excluded=new HashMap<>(),demoted=new HashMap<>();
    public UserDictionary(List<Entry> values){
        entries=List.copyOf(new LinkedHashSet<>(values));
        if(entries.size()>MAX_ENTRIES)throw new IllegalArgumentException("辞書は10万件まで登録できます");
        for(Entry entry:entries){
            if(entry.partOfSpeech().equals("抑制単語")||entry.partOfSpeech().equals("除外")||entry.partOfSpeech().equals("抑制")){
                Map<String,Set<String>> policy=entry.partOfSpeech().equals("抑制")?demoted:excluded;
                policy.computeIfAbsent(entry.reading(),key->new HashSet<>()).add(entry.word());continue;
            }
        }
        for(Entry entry:entries){
            if(entry.partOfSpeech().equals("抑制単語")||entry.partOfSpeech().equals("抑制")||isExcluded(entry.reading(),entry.word()))continue;
            Node node=root;for(char ch:entry.reading().toCharArray())node=node.children.computeIfAbsent(ch,key->new Node());
            node.words.add(entry.word());
        }
    }
    public List<Entry> entries(){return entries;}
    public boolean hasExclusions(){return !excluded.isEmpty();}
    private static boolean matches(Map<String,Set<String>> rules,String reading,String word){
        Set<String> any=rules.get(""),exact=rules.get(normalize(reading));
        return any!=null&&any.contains(word)||exact!=null&&(exact.contains("")||exact.contains(word));
    }
    public boolean isExcluded(String reading,String word){return matches(excluded,reading,word);}
    public boolean isDemoted(String reading,String word){return matches(demoted,reading,word);}
    /** Freeze rules for a request, including typography transformations applied by the UI. */
    public CandidatePolicy policy(String reading,java.util.function.UnaryOperator<String> format){
        return new CandidatePolicy(wordsFor(excluded,reading,format),wordsFor(demoted,reading,format));
    }
    private static Set<String> wordsFor(Map<String,Set<String>> rules,String reading,java.util.function.UnaryOperator<String> format){
        Set<String> words=new HashSet<>();
        for(String key:new String[]{"",normalize(reading)}){
            Set<String> found=rules.get(key);if(found==null)continue;
            for(String word:found){words.add(word);if(!word.isEmpty())words.add(format.apply(word));}
        }
        return words;
    }
    public static final class CandidatePolicy {
        private final Set<String> excluded,demoted;
        private CandidatePolicy(Set<String> excluded,Set<String> demoted){this.excluded=excluded;this.demoted=demoted;}
        public boolean allows(String word){return !excluded.contains("")&&!excluded.contains(word);}
        public List<String> apply(List<String> values){
            List<String> regular=new ArrayList<>(),lower=new ArrayList<>();
            for(String value:values)if(allows(value))(demoted.contains("")||demoted.contains(value)?lower:regular).add(value);
            regular.addAll(lower);return regular;
        }
    }
    public static String normalize(String reading){
        String value=Normalizer.normalize(reading,Normalizer.Form.NFKC);
        StringBuilder result=new StringBuilder();
        value.codePoints().forEach(cp->result.appendCodePoint(cp>=0x30a1&&cp<=0x30f6?cp-0x60:cp));
        return result.toString();
    }
    public static UserDictionary read(Reader source)throws IOException {
        BufferedReader reader=new BufferedReader(source);List<Entry> entries=new ArrayList<>();
        String line;int number=0;
        while((line=reader.readLine())!=null){
            number++;if(number==1&&line.startsWith("\ufeff"))line=line.substring(1);
            if(line.isBlank()||line.startsWith("#")||line.startsWith("!"))continue;
            String[] fields=line.split("\t",-1);
            if(fields.length<3||fields.length>4)throw new IOException(number+"行目：よみ・単語・品詞をタブで区切ってください");
            try{entries.add(new Entry(fields[0],fields[1],fields[2],fields.length==4?fields[3]:""));}
            catch(IllegalArgumentException e){throw new IOException(number+"行目："+e.getMessage(),e);}
            if(entries.size()>MAX_ENTRIES)throw new IOException("辞書は10万件まで登録できます");
        }
        return new UserDictionary(entries);
    }
    public void write(Writer out)throws IOException {
        for(Entry entry:entries)out.write(entry.reading()+"\t"+entry.word()+"\t"+entry.partOfSpeech()+"\t"+entry.comment()+"\n");
    }
    public UserDictionary merge(UserDictionary other){List<Entry> all=new ArrayList<>(entries);all.addAll(other.entries);return new UserDictionary(all);}
    public List<String> exact(String reading){
        Node node=root;for(char ch:normalize(reading).toCharArray()){node=node.children.get(ch);if(node==null)return List.of();}
        return List.copyOf(node.words);
    }
    /** Prefer whole-reading entries; also offer a sentence using longest registered readings. */
    public List<String> candidates(String reading,BiFunction<String,Integer,List<String>> converter,BooleanSupplier cancelled){
        String input=normalize(reading);LinkedHashSet<String> result=new LinkedHashSet<>(exact(input));
        List<String> sentences=new ArrayList<>(List.of(""));int plainStart=0;boolean matched=false;
        for(int start=0;start<input.length();){
            if(cancelled.getAsBoolean())return List.of();
            Node node=root,best=null;int end=start;
            for(int at=start;at<input.length();at++){
                node=node.children.get(input.charAt(at));if(node==null)break;
                if(!node.words.isEmpty()){best=node;end=at+1;}
            }
            if(best==null){start+=Character.charCount(input.codePointAt(start));continue;}
            if(start>plainStart)append(sentences,firstOrReading(converter,input.substring(plainStart,start)));
            List<String> expanded=new ArrayList<>();
            outer:for(String prefix:sentences)for(String word:best.words){expanded.add(prefix+word);if(expanded.size()>=32)break outer;}
            sentences=expanded;matched=true;start=end;plainStart=end;
        }
        if(matched){
            if(plainStart<input.length())append(sentences,firstOrReading(converter,input.substring(plainStart)));
            result.addAll(sentences);
        }
        return new ArrayList<>(result);
    }
    private static String firstOrReading(BiFunction<String,Integer,List<String>> converter,String reading){List<String> values=converter.apply(reading,1);return values.isEmpty()?reading:values.get(0);}
    private static void append(List<String> values,String suffix){values.replaceAll(prefix->prefix+suffix);}
}
