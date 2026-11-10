package net.neomenia.glideprototype;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import org.json.*;
import net.neomenia.inputcore.UserDictionary;

/** Private, atomic storage; immutable snapshots shared with conversion workers. */
final class UserDictionaryStore {
    private static UserDictionaryStore instance;
    static synchronized UserDictionaryStore get(Context context){
        File path=new File(context.getFilesDir(),"user-dictionary.tsv");
        if(instance==null||!instance.legacy.getBaseFile().equals(path))instance=new UserDictionaryStore(path);
        return instance;
    }
    private final AtomicFile file;
    private final AtomicFile legacy;
    record Group(String id,String name,UserDictionary dictionary) {}
    private List<Group> groups;
    private volatile UserDictionary snapshot;
    UserDictionary peek(){return snapshot;}
    UserDictionaryStore(File path){legacy=new AtomicFile(path);file=new AtomicFile(new File(path.getParentFile(),"user-dictionaries.json"));}
    synchronized UserDictionary load()throws IOException {
        if(snapshot==null){
            try(InputStream in=file.openRead()){
                JSONObject root=new JSONObject(new String(readBytes(in),StandardCharsets.UTF_8));
                if(root.getInt("version")!=1)throw new IOException("未対応の辞書形式です");
                JSONArray items=root.getJSONArray("dictionaries");List<Group> loaded=new ArrayList<>();Set<String> ids=new HashSet<>(),names=new HashSet<>();
                for(int i=0;i<items.length();i++){
                    JSONObject item=items.getJSONObject(i);String id=item.getString("id"),name=validName(item.getString("name"));
                    if(id.isBlank()||!ids.add(id)||!names.add(name))throw new IOException("辞書情報が不正です");
                    loaded.add(new Group(id,name,UserDictionary.read(new StringReader(item.getString("entries")))));
                }
                if(loaded.isEmpty())throw new IOException("辞書がありません");
                UserDictionary combined=combine(loaded);groups=List.copyOf(loaded);snapshot=combined;
            }catch(FileNotFoundException e){
                UserDictionary original;
                try(InputStream in=legacy.openRead()){original=readImport(in);}
                catch(FileNotFoundException missing){original=new UserDictionary(List.of());}
                groups=List.of(new Group("default","標準辞書",original));snapshot=original;
            }catch(JSONException|IllegalArgumentException e){throw new IOException("辞書情報を読み込めません",e);}
        }
        return snapshot;
    }
    synchronized List<Group> groups()throws IOException {load();return groups;}
    synchronized Group group(String id)throws IOException {
        load();return groups.stream().filter(group->group.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("辞書が見つかりません"));
    }
    synchronized String create(String name)throws IOException {
        return create(name,new UserDictionary(List.of()));
    }
    synchronized String create(String name,UserDictionary dictionary)throws IOException {
        load();name=uniqueName(name,null);String id=UUID.randomUUID().toString();List<Group> changed=new ArrayList<>(groups);
        changed.add(new Group(id,name,dictionary));persist(changed);return id;
    }
    synchronized void rename(String id,String name)throws IOException {
        Group original=group(id);name=uniqueName(name,id);List<Group> changed=new ArrayList<>(groups);
        changed.set(changed.indexOf(original),new Group(id,name,original.dictionary()));persist(changed);
    }
    synchronized void delete(String id)throws IOException {
        Group original=group(id);if(groups.size()==1)throw new IllegalArgumentException("最後の辞書は削除できません");
        List<Group> changed=new ArrayList<>(groups);changed.remove(original);persist(changed);
    }
    synchronized void save(String id,UserDictionary dictionary)throws IOException {
        Group original=group(id);List<Group> changed=new ArrayList<>(groups);
        changed.set(changed.indexOf(original),new Group(id,original.name(),dictionary));persist(changed);
    }
    synchronized void edit(String sourceId,String destinationId,UserDictionary.Entry original,UserDictionary.Entry replacement)throws IOException {
        Group source=group(sourceId),destination=group(destinationId);List<Group> changed=new ArrayList<>(groups);
        List<UserDictionary.Entry> entries=new ArrayList<>(source.dictionary().entries());int index=original==null?-1:entries.indexOf(original);
        if(original!=null&&index<0)throw new IllegalArgumentException("単語が見つかりません");
        if(index>=0)entries.remove(index);
        if(sourceId.equals(destinationId)&&replacement!=null)entries.add(index<0?entries.size():index,replacement);
        changed.set(changed.indexOf(source),new Group(sourceId,source.name(),new UserDictionary(entries)));
        if(!sourceId.equals(destinationId)&&replacement!=null){
            List<UserDictionary.Entry> target=new ArrayList<>(destination.dictionary().entries());target.add(replacement);
            changed.set(changed.indexOf(destination),new Group(destinationId,destination.name(),new UserDictionary(target)));
        }
        persist(changed);
    }
    private static String validName(String name){
        name=name.strip();if(name.isEmpty()||name.length()>100||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("辞書名は改行を含めず1〜100文字で入力してください");return name;
    }
    private String uniqueName(String name,String id){
        String value=validName(name);if(groups.stream().anyMatch(group->!group.id().equals(id)&&group.name().equals(value)))throw new IllegalArgumentException("同じ名前の辞書があります");return value;
    }
    private static UserDictionary combine(List<Group> values){
        if(values.size()==1)return values.get(0).dictionary();
        List<UserDictionary.Entry> entries=new ArrayList<>();for(Group group:values)entries.addAll(group.dictionary().entries());return new UserDictionary(entries);
    }
    synchronized void save(UserDictionary dictionary)throws IOException {
        load();save(groups.get(0).id(),dictionary);
    }
    private void persist(List<Group> changed)throws IOException {
        UserDictionary combined=combine(changed);byte[] bytes;
        try{
            JSONArray items=new JSONArray();for(Group group:changed){StringWriter entries=new StringWriter();group.dictionary().write(entries);items.put(new JSONObject().put("id",group.id()).put("name",group.name()).put("entries",entries.toString()));}
            bytes=new JSONObject().put("version",1).put("dictionaries",items).toString().getBytes(StandardCharsets.UTF_8);
        }catch(JSONException e){throw new IOException("辞書を保存できません",e);}
        if(bytes.length>32*1024*1024)throw new IOException("辞書は合計32MBまで保存できます");
        FileOutputStream out=file.startWrite();
        try{
            out.write(bytes);file.finishWrite(out);groups=List.copyOf(changed);snapshot=combined;
        }catch(IOException|RuntimeException e){file.failWrite(out);throw e;}
    }
    static UserDictionary readImport(InputStream in)throws IOException {
        // Bound untrusted document input before decoding. Mozc exports UTF-8 or UTF-16.
        byte[] data=readBytes(in);Charset charset=StandardCharsets.UTF_8;
        if(data.length>=2&&((data[0]==(byte)0xff&&data[1]==(byte)0xfe)||(data[0]==(byte)0xfe&&data[1]==(byte)0xff)))charset=StandardCharsets.UTF_16;
        try(Reader reader=new InputStreamReader(new ByteArrayInputStream(data),charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))){return UserDictionary.read(reader);}
    }
    private static byte[] readBytes(InputStream in)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=in.read(buffer))!=-1){if(bytes.size()+count>32*1024*1024)throw new IOException("辞書ファイルは32MBまで読み込めます");bytes.write(buffer,0,count);}
        return bytes.toByteArray();
    }
}
