package net.neomenia.glideprototype;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.*;
import net.neomenia.inputcore.UserDictionary;

/** Private, atomic storage; immutable snapshots shared with conversion workers. */
final class UserDictionaryStore {
    private static UserDictionaryStore instance;
    static synchronized UserDictionaryStore get(Context context){
        File path=new File(context.getFilesDir(),"user-dictionary.tsv");
        if(instance==null||!instance.file.getBaseFile().equals(path))instance=new UserDictionaryStore(path);
        return instance;
    }
    private final AtomicFile file;
    private volatile UserDictionary snapshot;
    UserDictionary peek(){return snapshot;}
    private UserDictionaryStore(File path){file=new AtomicFile(path);}
    synchronized UserDictionary load()throws IOException {
        if(snapshot==null){
            try(InputStream in=file.openRead()){snapshot=readImport(in);}
            catch(FileNotFoundException e){snapshot=new UserDictionary(java.util.List.of());}
        }
        return snapshot;
    }
    synchronized void save(UserDictionary dictionary)throws IOException {
        FileOutputStream out=file.startWrite();
        try{
            OutputStream bounded=new FilterOutputStream(out){
                private long count;
                @Override public void write(int value)throws IOException {if(++count>32*1024*1024)throw new IOException("辞書は32MBまで保存できます");out.write(value);}
                @Override public void write(byte[] bytes,int offset,int length)throws IOException {if((count+=length)>32*1024*1024)throw new IOException("辞書は32MBまで保存できます");out.write(bytes,offset,length);}
            };
            Writer writer=new BufferedWriter(new OutputStreamWriter(bounded,StandardCharsets.UTF_8));dictionary.write(writer);writer.flush();file.finishWrite(out);snapshot=dictionary;
        }catch(IOException|RuntimeException e){file.failWrite(out);throw e;}
    }
    static UserDictionary readImport(InputStream in)throws IOException {
        // Bound untrusted document input before decoding. Mozc exports UTF-8 or UTF-16.
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=in.read(buffer))!=-1){if(bytes.size()+count>32*1024*1024)throw new IOException("辞書ファイルは32MBまで読み込めます");bytes.write(buffer,0,count);}
        byte[] data=bytes.toByteArray();Charset charset=StandardCharsets.UTF_8;
        if(data.length>=2&&((data[0]==(byte)0xff&&data[1]==(byte)0xfe)||(data[0]==(byte)0xfe&&data[1]==(byte)0xff)))charset=StandardCharsets.UTF_16;
        try(Reader reader=new InputStreamReader(new ByteArrayInputStream(data),charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))){return UserDictionary.read(reader);}
    }
}
