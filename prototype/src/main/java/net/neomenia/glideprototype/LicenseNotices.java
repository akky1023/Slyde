package net.neomenia.glideprototype;

import android.content.Context;
import java.io.*;

final class LicenseNotices {
    static final String[] GROUPS = {"Slyde本体", "Mozc", "Sumire", "zenz"};
    static String read(Context context, String group) throws IOException {
        String summary;String[][] notices;
        switch(group){
            case "Slyde本体"->{
                summary="Slyde本体はMIT Licenseで公開しています。\n画面表示・フォント・記号名に使用する素材とライブラリの通知も掲載しています。";
                notices=new String[][]{{"Slyde本体 — MIT License","slyde-MIT.txt"},{"Material Components for Android / AndroidX — Apache License 2.0","zenz-model-Apache-2.0.txt"},{"GitHub Octicons — MIT License","github-octicons-MIT.txt"},{"Microsoft Fluent UI System Icons — MIT License","fluentui-system-icons-MIT.txt"},{"Unicode","unicode.txt"},{"CLDR","cldr.txt"},{"Noto Sans JP — SIL Open Font License","NotoSansJP-OFL.txt"},{"Noto Sans JP — Notices","NotoSansJP-Notices.txt"},{"Bizin Gothic Discord — SIL Open Font License","BizinGothicDiscord-OFL.txt"},{"Bizin Gothic Discord — Notices","BizinGothicDiscord-Regular-Notices.txt"},{"Noto Sans Symbols — SIL Open Font License","NotoSymbols-OFL.txt"}};
            }
            case "Mozc"->{
                summary="読み付きの記号・絵文字辞書を使用しています。\nMozcのライセンスと第三者の通知を掲載しています。";
                notices=new String[][]{{"Mozc — Copyright / License / Third-party notices","mozc.txt"}};
            }
            case "Sumire"->{
                summary="Sumireの基本辞書とkotlin-kana-kanji-converterの変換処理を使用しています。\nコードのMIT Licenseと、辞書・データの個別の利用条件を掲載しています。";
                notices=new String[][]{{"Sumire — MIT License","sumire-MIT.txt"},{"kotlin-kana-kanji-converter — MIT License","sumire-converter-MIT.txt"},{"IPADIC — License / Notices","ipadic.txt"},{"IPADIC — COPYING","ipadic-copying.txt"},{"Data — License / Attribution","data.txt"},{"Mozc由来の辞書 — License / Notices","mozc.txt"}};
            }
            case "zenz"->{
                summary="端末内のAI変換にzenzを使用しています。\nJNIブリッジ・llama.cppはMIT License、zenzモデルはApache License 2.0です。";
                notices=new String[][]{{"Sumire JNI bridge — MIT License","zenz-bridge-MIT.txt"},{"llama.cpp — MIT License","llama-MIT.txt"},{"zenzモデル — Apache License 2.0","zenz-model-Apache-2.0.txt"}};
            }
            default->throw new IllegalArgumentException(group);
        }
        StringBuilder text=new StringBuilder(summary).append("\n\n");
        for(String[] notice:notices){
            text.append("■ ").append(notice[0]).append("\n\n");
            try(InputStream in=context.getAssets().open("licenses/"+notice[1])){
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] buffer=new byte[4096];
                int count;
                while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                text.append(out.toString("UTF-8")).append("\n\n");
            }
        }
        return text.toString();
    }
}
