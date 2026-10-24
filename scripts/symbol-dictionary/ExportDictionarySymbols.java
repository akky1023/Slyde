import net.neomenia.inputcore.SymbolCatalog;
public class ExportDictionarySymbols {
 public static void main(String[] args) throws Exception {
  try(var out=java.nio.file.Files.newBufferedWriter(java.nio.file.Path.of(args[0]),java.nio.charset.StandardCharsets.UTF_8)){
   for(var c:SymbolCatalog.load())for(var e:c.entries())out.write(c.name()+"\t"+e.value()+"\t"+e.originalValue()+"\t"+e.name()+"\n");
  }
 }
}
