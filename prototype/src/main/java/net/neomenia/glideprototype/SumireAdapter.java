package net.neomenia.glideprototype;

import android.content.Context;
import com.kazumaproject.engine.KanaKanjiEngine;
import java.io.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import java.util.function.BooleanSupplier;
import net.neomenia.inputcore.CandidateCache;
import net.neomenia.inputcore.ConversionCandidates;

/** Source engine by Sumire's author plus pinned system assets from Android Sumire. */
final class SumireAdapter {
    private static SumireAdapter instance;
    static synchronized SumireAdapter get(Context context) throws IOException {
        if(instance==null)instance=new SumireAdapter(context.getApplicationContext());return instance;
    }
    private final KanaKanjiEngine engine=new KanaKanjiEngine();
    private final CandidateCache cache=new CandidateCache(128);
    private final ConversionCandidates conversion=new ConversionCandidates();
    SumireAdapter(Context context) throws IOException {
        File dir=new File(context.getFilesDir(),"sumire-fb831989-d5bcefcf");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("辞書保存先を作成できません");
        for(String name:new String[]{"yomi.dat","tango.dat","token.dat","connectionId.dat","pos_table.dat"}){
            File target=new File(dir,name);
            if(target.isFile()&&target.length()>0)continue;
            File temp=new File(dir,name+".tmp");
            if(name.equals("pos_table.dat")){
                try(InputStream in=context.getAssets().open("sumire/"+name);OutputStream out=new FileOutputStream(temp)){copy(in,out);}
            }else{
                try(ZipInputStream in=new ZipInputStream(context.getAssets().open("sumire/"+name+".zip"));OutputStream out=new FileOutputStream(temp)){
                    if(in.getNextEntry()==null)throw new IOException("辞書が空です: "+name);copy(in,out);
                }
            }
            if(!temp.renameTo(target))throw new IOException("辞書を保存できません: "+name);
        }
        engine.buildEngineFromResourceDirectory(dir.getAbsolutePath(),1);
    }
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    List<String> cachedCandidates(String reading){return cache.get(reading);}
    synchronized boolean accepts(String reading,String value,BooleanSupplier cancelled){return engine.matchesReading(reading,value,cancelled);}
    synchronized List<String> candidates(String reading,BooleanSupplier cancelled){
        if(reading.isEmpty()||cancelled.getAsBoolean())return List.of();
        List<String> cached=cache.get(reading);if(cached!=null)return cached;
        List<String> values=conversion.convert(reading,(part,count)->engine.candidates(part,count,cancelled),cancelled);
        if(!cancelled.getAsBoolean())cache.put(reading,values);return values;
    }
}
