"""Build the offline Unicode Japanese-reading dictionary; see docs/SYMBOLS.md."""
from pathlib import Path
from collections import Counter
from functools import lru_cache
import argparse, hashlib, json, os, re, subprocess, sys, unicodedata as ud
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "scripts/symbol-dictionary"
DATA = ROOT / ".tools/symbol-dictionary"
OUTPUT = ROOT / "input-core/src/main/resources/conversion/unicode-symbols.tsv"
sys.path.insert(0, str(DATA / "python"))
from pykakasi import kakasi

KAKASI = kakasi()
LETTERS = dict(zip("ABCDEFGHIJKLMNOPQRSTUVWXYZ", "エー ビー シー ディー イー エフ ジー エイチ アイ ジェー ケー エル エム エヌ オー ピー キュー アール エス ティー ユー ブイ ダブリュー エックス ワイ ゼット".split()))
NUMBER_WORDS = dict(zip("ZERO ONE TWO THREE FOUR FIVE SIX SEVEN EIGHT NINE TEN ELEVEN TWELVE THIRTEEN FOURTEEN FIFTEEN SIXTEEN SEVENTEEN EIGHTEEN NINETEEN TWENTY THIRTY FORTY FIFTY SIXTY SEVENTY EIGHTY HUNDRED THOUSAND".split(), "零 一 二 三 四 五 六 七 八 九 十 十一 十二 十三 十四 十五 十六 十七 十八 十九 二十 三十 四十 五十 六十 七十 八十 百 千".split()))

def number(n):
    if n == 0: return "ゼロ"
    result = ""
    for place, unit in [(10000,"万"),(1000,"千"),(100,"百"),(10,"十"),(1,"")]:
        q,n=divmod(n,place)
        if q: result += (number(q) if q>9 else "一二三四五六七八九"[q-1] if q!=1 or place in (10000,1) else "")+unit
    return result

@lru_cache(maxsize=40000)
def kana(text):
    text=ud.normalize("NFKC",text)
    text=text.replace("μ","ミュー").replace("Ω","オーム")
    text=re.sub(r"\d+",lambda m:number(int(m[0])),text)
    text=re.sub(r"[A-Za-z]+",lambda m:"".join(LETTERS[c] for c in m[0].upper()),text)
    value="".join(item["hira"] for item in KAKASI.convert(text))
    return "".join(chr(ord(c)-0x60) if "ァ"<=c<="ヶ" else c for c in value if c.isalpha() or c=="ー")

def annotations(locale):
    names,keywords={},{}
    for folder in ("annotations","annotationsDerived"):
        for a in ET.parse(DATA/f"{folder}-{locale}.xml").iter("annotation"):
            key=a.get("cp").replace("\ufe0f","")
            if a.get("type")=="tts": names[key]=a.text
            else: keywords[key]=[v.strip() for v in a.text.split("|")]
    return names,keywords

def export_catalog():
    java=Path(os.environ["JAVA_HOME"])/"bin"
    classes=DATA/"classes";classes.mkdir(parents=True,exist_ok=True)
    core=ROOT/"input-core/src/main/java/net/neomenia/inputcore"
    subprocess.run([str(java/"javac.exe"),"-encoding","UTF-8","-d",str(classes),str(CONFIG/"ExportDictionarySymbols.java"),str(core/"SymbolCatalog.java"),str(core/"SymbolPresentation.java")],check=True)
    subprocess.run([str(java/"java.exe"),"-cp",os.pathsep.join([str(classes),str(ROOT/"input-core/src/main/resources")]),"ExportDictionarySymbols",str(DATA/"catalog.tsv")],check=True)
    return [line.split("\t",3) for line in (DATA/"catalog.tsv").read_text(encoding="utf-8").splitlines()]

def main():
    parser=argparse.ArgumentParser();parser.add_argument("--fetch",action="store_true");args=parser.parse_args()
    manifest=json.loads((CONFIG/"sources.json").read_text(encoding="utf-8"))
    DATA.mkdir(parents=True,exist_ok=True)
    for filename,source in manifest["sources"].items():
        path=DATA/filename
        if not path.exists() and args.fetch: urllib.request.urlretrieve(source["url"],path)
        if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest()!=source["sha256"]: raise SystemExit("Missing or changed source: "+filename+" (use --fetch)")
    catalog=export_catalog();ja,keywords=annotations("ja");en,_=annotations("en")
    records={int(parts[0],16):parts for line in (DATA/"UnicodeData.txt").read_text(encoding="utf-8").splitlines() for parts in [line.split(";")]}
    terms={}
    for line in (CONFIG/"unicode-terms.tsv").read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#"):
            key,value=line.split("\t",1);terms[key]=value
    terms.update(NUMBER_WORDS);unknown=Counter()
    phrases=sorted((k for k in terms if " " in k or "-" in k),key=len,reverse=True)
    def translate(name):
        for phrase in phrases: name=name.replace(phrase,terms[phrase])
        def word(m):
            token=m[0]
            if token in terms:return terms[token]
            if len(token)==1:return LETTERS[token]
            unknown[token]+=1;return "<"+token+">"
        return re.sub(r"[A-Z]+",word,name).replace("-","").replace(" ","")
    def special(category,key,name):
        if len(key)!=1:return None,[]
        cp=ord(key);plain=ud.normalize("NFKC",key)
        if category=="点字":
            dots=name.split("DOTS-",1)[-1] if "DOTS-" in name else ""
            return ("点字の"+"・".join(dots) if dots else "点字の空白"),["てんじ"+"".join(kana(c) for c in dots)] if dots else ["てんじくうはく"]
        if category=="囲み日本語":
            content=plain.strip("()")
            return ("括弧付き" if plain.startswith("(") else "丸囲み")+content,["まる"+kana(content),"かっこ"+kana(content)]
        if category=="囲み英数字":
            content=plain.strip("()")
            numeric=records[cp][8]
            if numeric and "/" not in numeric: content=number(int(numeric))
            return ("括弧付き" if plain.startswith("(") else "丸囲み")+content,["まる"+kana(content)]
        if category=="組み文字":
            eras={"㋿":"令和","㍻":"平成","㍼":"昭和","㍽":"大正","㍾":"明治"}
            if key in eras:return eras[key]+"の元号記号",[kana(eras[key]),kana(eras[key])+"きごう"]
            if 0x32c0<=cp<=0x32cb:return str(cp-0x32c0+1)+"月の電報記号",[kana(str(cp-0x32c0+1)+"がつきごう")]
            if 0x3358<=cp<=0x3370:return str(cp-0x3358)+"時の電報記号",[kana(str(cp-0x3358)+"じきごう")]
            if 0x33e0<=cp<=0x33fe:return str(cp-0x33e0+1)+"日の電報記号",[kana(str(cp-0x33e0+1)+"にちきごう")]
            units={"kg":"キログラム","km":"キロメートル","mm":"ミリメートル","cm":"センチメートル","m2":"平方メートル","m3":"立方メートル","cc":"シーシー","mg":"ミリグラム","ml":"ミリリットル","kHz":"キロヘルツ","MHz":"メガヘルツ","GHz":"ギガヘルツ"}
            units.update({"μA":"マイクロアンペア","mA":"ミリアンペア","kA":"キロアンペア","μg":"マイクログラム","ng":"ナノグラム","μm":"マイクロメートル","nm":"ナノメートル","dm":"デシメートル","μl":"マイクロリットル","dl":"デシリットル","kl":"キロリットル","Hz":"ヘルツ","ns":"ナノ秒","μs":"マイクロ秒","ms":"ミリ秒","ps":"ピコ秒","μF":"マイクロファラド","pF":"ピコファラド","μW":"マイクロワット","mW":"ミリワット","kW":"キロワット","MW":"メガワット","μV":"マイクロボルト","mV":"ミリボルト","kV":"キロボルト","MV":"メガボルト","kΩ":"キロオーム","MΩ":"メガオーム","Pa":"パスカル","kPa":"キロパスカル","MPa":"メガパスカル","GPa":"ギガパスカル","Bq":"ベクレル","Gy":"グレイ","Sv":"シーベルト","Wb":"ウェーバ","cd":"カンデラ","lm":"ルーメン","lx":"ルクス","mol":"モル","rad":"ラジアン","sr":"ステラジアン","ha":"ヘクタール"})
            content=units.get(plain,plain)
            return "組み文字"+content,[kana(content),"くみ"+kana(content)]
        if category=="分数・ローマ数字":
            if "ROMAN NUMERAL" in name and records[cp][8].isdigit():
                content=number(int(records[cp][8]));return "ローマ数字"+content,["ろーま"+kana(content)]
            match=re.fullmatch(r"(\d+)⁄(\d+)",plain)
            if match:
                a,b=match.groups();return b+"分の"+a,[kana(b+"ぶんの"+a)]
        return None,[]

    output=[];stats=Counter();broad={"括弧":"かっこ","数学":"すうがくきごう","矢印":"やじるし","通貨":"つうかきごう","単位":"たんいきごう","囲み英数字":"まるすうじ","囲み日本語":"かこみもじ","囲みハングル":"かこみはんぐる","組み文字":"くみもじ","罫線":"けいせん","図形":"ずけい","装飾・音楽":"そうしょくきごう","点字":"てんじ","特殊記号":"とくしゅきごう"}
    # Common readings precede generated names, while the complete repertoire follows.
    for line in (CONFIG/"conventional.tsv").read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):continue
        readings,values=line.split("\t",1)
        for value in values.split():output.append(["SYMBOL",value,readings,"慣習的な読み","","curated"])
    overrides={"🇨🇶":"サーク島の旗","仝":"同上記号"}
    blocked={"記号","文字","顔","人","手","動物","自然","上","下","右","左","白","黒","赤","青","緑","色","絵文字","数字"}
    for category,value,original,description in catalog:
        # Omit skin/hair variants and standalone components; keep the neutral base entries.
        if any(0x1f3fb<=ord(c)<=0x1f3ff or 0x1f9b0<=ord(c)<=0x1f9b3 for c in original):continue
        value=original.replace("\ufe0f","").replace("\ufe0e","")
        key=original.replace("\ufe0f","").replace("\ufe0e","");emoji=category.startswith("絵文字：")
        english=en.get(key) if emoji else records.get(ord(key),["",ud.name(key,"")])[1]
        if not english:english=re.sub(r"^.*? E[0-9.]+ ","",description)
        label=overrides.get(key) or ja.get(key);extra=[];source="curated" if key in overrides else "CLDR"
        if not label:
            label,extra=special(category,key,english)
            if not label:label=translate(english)
            source="Unicode name"
        readings=[kana(label),*extra]
        # Unicode names remain available as exact, space-free English lookup keys too.
        readings.extend([re.sub(r"[^a-z0-9]","",english.lower()),"u"+"_".join(f"{ord(c):x}" for c in key)])
        if emoji:
            readings.extend(kana(word) for word in keywords.get(key,[]) if word not in blocked and len(word)>=2)
        elif category in broad:readings.append(broad[category])
        readings=list(dict.fromkeys(r for r in readings if r))
        if not re.fullmatch(r"[ぁ-ゖー]+",readings[0]) and "<" not in label:raise SystemExit("Invalid kana reading: "+repr((value,label,readings[0])))
        output.append(["EMOJI" if emoji else "SYMBOL",value," ".join(readings),label,english,source])
        stats[source]+=1
    (DATA/"unknown-terms.txt").write_text("\n".join(f"{word}\t{n}" for word,n in unknown.most_common()),encoding="utf-8")
    if unknown:
        print("Untranslated terms",len(unknown));print(unknown.most_common(50));raise SystemExit(1)
    with OUTPUT.open("w",encoding="utf-8",newline="\n") as stream:
        stream.write("POS\tCHAR\tReading (space separated)\tJapanese name\tUnicode English name\tSource\n")
        for row in output:stream.write("\t".join(row)+"\n")
    print("Generated",len(output),"symbols;",dict(stats),"bytes",OUTPUT.stat().st_size)
    # Only explicit Unicode width mappings: no compatibility folding of enclosed text or units.
    # Exclude halfwidth kana/Hangul letters; punctuation, Latin letters and digits remain paired.
    pairs=[]
    for cp,fields in sorted(records.items()):
        decomposition=fields[5].split()
        if len(decomposition)!=2 or decomposition[0] not in ("<wide>","<narrow>") or 0xff66<=cp<=0xffdc:continue
        other=int(decomposition[1],16)
        wide,narrow=(cp,other) if decomposition[0]=="<wide>" else (other,cp)
        pairs.append(f"{wide:04X}\t{narrow:04X}")
    (OUTPUT.parent/"width-variants.tsv").write_text("# Unicode 16.0 explicit wide/narrow pairs; hexadecimal code points\n"+"\n".join(pairs)+"\n",encoding="utf-8")
    print("Generated",len(pairs),"width pairs")

if __name__=="__main__":main()
