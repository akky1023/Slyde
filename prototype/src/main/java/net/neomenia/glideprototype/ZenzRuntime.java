package net.neomenia.glideprototype;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.kazumaproject.zenz.ZenzEngine;
import net.neomenia.inputcore.ZenzCandidates;
import net.neomenia.inputcore.CandidateCache;
import net.neomenia.inputcore.ConversionPlan;
import java.util.List;
import java.io.*;
import java.security.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** One process-wide model and actor; dictionary conversion never waits on this executor. */
final class ZenzRuntime {
    private static ZenzRuntime instance;
    static synchronized ZenzRuntime get(Context context){if(instance==null)instance=new ZenzRuntime(context.getApplicationContext());return instance;}
    private static final String MODEL="zenz-v3.2-xsmall-Q5_K_M.gguf";
    private static final String SHA="00c64b3d318045a708d0cad5434faccab10f5481a49e6362864551fd0995fa58";
    private final ExecutorService actor=Executors.newSingleThreadExecutor(task->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND+2);task.run();},"zenz"));
    private final CandidateCache cache=new CandidateCache(32);
    private final AtomicLong sequence=new AtomicLong();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Context context;
    private volatile ZenzEngine engine;
    private boolean loaded=false;
    private String failure;
    private ZenzRuntime(Context context){this.context=context;}
    void cancel(){sequence.incrementAndGet();ZenzEngine current=engine;if(current!=null)current.cancelCurrent();}
    void warmup(Consumer<String> status){actor.execute(()->{String error=initialize();main.post(()->status.accept(error));});}
    private String initialize(){
        if(loaded)return null;if(failure!=null)return failure;
        try{
            File model=new File(context.getFilesDir(),MODEL);
            if(!model.isFile()||model.length()!=20970304){
                File temporary=new File(context.getFilesDir(),MODEL+".tmp");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(InputStream input=context.getAssets().open("zenz/"+MODEL);OutputStream output=new FileOutputStream(temporary)){
                    byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1){output.write(buffer,0,count);digest.update(buffer,0,count);}
                }
                StringBuilder hex=new StringBuilder();for(byte value:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
                if(!SHA.contentEquals(hex))throw new IOException("zenzモデルの検証に失敗しました");
                if(!temporary.renameTo(model))throw new IOException("zenzモデルを保存できません");
            }
            engine=ZenzEngine.INSTANCE;engine.setRuntimeConfig(1024,Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()/2)));
            if(!engine.initModel(model.getAbsolutePath()))throw new IOException("zenzモデルを読み込めません");loaded=true;return null;
        }catch(Exception|LinkageError|OutOfMemoryError error){failure=error.getClass().getSimpleName()+": "+error.getMessage();return failure;}
    }
    void request(String reading,String left,String right,Consumer<String> result){
        cancel();long request=sequence.get();
        String key=left+'\0'+reading+'\0'+right;
        actor.execute(()->{
            if(sequence.get()!=request)return;
            List<String> cached=cache.get(key);
            if(cached!=null){main.post(()->{if(sequence.get()==request)result.accept(cached.get(0));});return;}
            if(initialize()!=null||sequence.get()!=request)return;
            try{
                StringBuilder converted=new StringBuilder();
                for(var part:ConversionPlan.split(reading,64)){
                    if(sequence.get()!=request)return;
                    if(!part.convert()){converted.append(part.text());continue;}
                    String prior=ConversionPlan.tail(left+converted,64),following=part.end()==reading.length()?ConversionPlan.head(right,32):"";
                    int budget=Math.max(128,Math.min(384,part.text().codePointCount(0,part.text().length())*3+32));
                    String output=ZenzCandidates.normalize(engine.generateWithContextAndConditionsV32("","","","",prior,following,part.text(),budget));
                    if(!ZenzCandidates.safe(output)||output.codePointCount(0,output.length())<part.text().codePointCount(0,part.text().length())/4)return;
                    if(!SumireAdapter.get(context).accepts(part.text(),output,()->sequence.get()!=request))return;
                    converted.append(output);
                }
                String value=converted.toString();
                if(value.equals(reading)&&reading.length()>8)return;
                if(sequence.get()==request&&ZenzCandidates.safe(value)){cache.put(key,List.of(value));main.post(()->{if(sequence.get()==request)result.accept(value);});}
            }catch(IOException|RuntimeException|OutOfMemoryError error){android.util.Log.e("ZenzRuntime","推論に失敗しました",error);}
        });
    }
}
