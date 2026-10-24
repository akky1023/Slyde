import com.kazumaproject.engine.KanaKanjiEngine
import org.junit.Assert.*
import org.junit.Test

class SumireEngineTest {
    @Test fun auditPresentationSelectorsInBundledDictionaryAndSymbolCandidates() {
        val engine=KanaKanjiEngine();engine.buildEngineFromResourceDirectory(System.getProperty("sumire.dictionary"),1)
        val trieField=KanaKanjiEngine::class.java.getDeclaredField("tangoTrie").apply { isAccessible=true }
        val tokenField=KanaKanjiEngine::class.java.getDeclaredField("tokenArray").apply { isAccessible=true }
        val trie=trieField.get(engine) as com.kazumaproject.Louds.LOUDS
        val tokens=tokenField.get(engine) as com.kazumaproject.dictionary.TokenArray
        val nodesField=tokens.javaClass.getDeclaredField("nodeIdList").apply { isAccessible=true }
        @Suppress("UNCHECKED_CAST") val nodes=nodesField.get(tokens) as List<Int>
        val affected=if(trie.labels.any { it=='\ufe0e'||it=='\ufe0f' })
            nodes.asSequence().filter { it>=0 }.distinct().map { trie.getLetter(it) }.filter { '\ufe0e' in it||'\ufe0f' in it }.toList()
            else emptyList()
        val report=java.io.File("../.tools/symbol-presentation-audit.tsv")
        report.writeText("# Base dictionary outputs with VS15/VS16: ${affected.size}\n"+affected.joinToString("\n") { value -> value+"\t"+value.codePoints().toArray().joinToString(" ") { "U+%04X".format(it) } }+"\n")
        val source=net.neomenia.inputcore.ConversionCandidates()
        for(reading in listOf("あすたりすく","あすたー","すたー","しゃーぷ","いげた","なんばー","はっしゅ","こめ","ほし","はーと","おんぷ","でんわ","はさみ","えんぴつ","たいよう","くも","かさ","ゆきだるま","ちぇっく")){
            val result=source.convert(reading,{part,count->engine.candidates(part,count,{false})},{false})
            report.appendText("$reading\t${result.take(20)}\n")
            assertTrue("$reading: $result",result.none { '\ufe0e' in it||'\ufe0f' in it })
        }
        val readings=linkedSetOf<String>()
        for(file in listOf("unicode-symbols.tsv","symbol.tsv","emoji_data.tsv")){
            javaClass.getResourceAsStream("/conversion/$file")!!.bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank()&&!it.startsWith("#")&&!it.startsWith("POS\t") }.forEach { line ->
                    readings.addAll(line.split('\t')[2].trim().split(Regex("\\s+")).filter { it.isNotEmpty() })
                }
            }
        }
        for(reading in readings)assertTrue(reading,source.symbols(reading).none { '\ufe0e' in it||'\ufe0f' in it })
        report.appendText("# Audited symbol/emoji reading keys: ${readings.size}; selector-bearing candidates: 0\n")
    }
    @Test fun symbolReadingsInsideSentencesDoNotOverrideWords() {
        val engine=KanaKanjiEngine();engine.buildEngineFromResourceDirectory(System.getProperty("sumire.dictionary"),1)
        val source=net.neomenia.inputcore.ConversionCandidates()
        for(reading in listOf("かっこいい","このかっこをなおす","かっこをつける","はーとのかたち","ほしがみえる")) {
            val result=source.convert(reading,{part,count->engine.candidates(part,count,{false})},{false})
            assertTrue(reading,result.isNotEmpty())
            assertFalse("$reading: ${result.take(8)}",result.take(8).any { value -> value.codePoints().anyMatch { cp ->
                Character.getType(cp) in listOf(Character.START_PUNCTUATION.toInt(),Character.END_PUNCTUATION.toInt(),Character.OTHER_SYMBOL.toInt(),Character.MATH_SYMBOL.toInt())
            } })
        }
        for(reading in listOf("かっこ。","このかっこ。","はーと。","ほし。")){
            val result=source.convert(reading,{part,count->engine.candidates(part,count,{false})},{false})
            assertFalse("$reading: ${result.first()}",result.first().contains("（）"))
            assertTrue(result.first().endsWith("。"))
        }
        assertTrue(source.convert("かっこ",{part,count->engine.candidates(part,count,{false})},{false}).contains("()"))
        assertTrue(source.convert("かっこ",{part,count->engine.candidates(part,count,{false})},{false}).contains("（）"))
    }
    @Test fun expandedCandidatesIncludeWholeReadingWordsAndMozcSymbols() {
        val engine=KanaKanjiEngine();engine.buildEngineFromResourceDirectory(System.getProperty("sumire.dictionary"),1)
        val source=net.neomenia.inputcore.ConversionCandidates()
        val active=java.util.function.BooleanSupplier{false}
        for(reading in listOf("こうしょう","かんしょう","きごう","やじるし","ほし","かっこ","ねこ")) {
            val ranked=engine.nBestPath(reading,12)
            val expanded=source.convert(reading,{part,count->engine.candidates(part,count,active)},active)
            assertEquals(reading,ranked.first(),expanded.first())
            assertTrue(reading,expanded.containsAll(ranked))
            assertTrue(reading,expanded.containsAll(source.symbols(reading).take(192)))
            assertTrue("$reading: ${expanded.size}",expanded.size>12)
        }
        assertTrue(engine.candidates("こうしょう",1,active).size>12)
        assertTrue(engine.candidates("にほん",64,java.util.function.BooleanSupplier{true}).isEmpty())
    }
    @Test fun generatedCandidatesMustCoverTheWholeReading() {
        val engine=KanaKanjiEngine();engine.buildEngineFromResourceDirectory(System.getProperty("sumire.dictionary"),1)
        val reading="あしたのかいぎでつかうしりょうをかくにんしてめーるでおくってください"
        val active=java.util.function.BooleanSupplier{false}
        assertTrue(engine.matchesReading(reading,"明日の会議で使う資料を確認してメールで送ってください",active))
        assertTrue(engine.matchesReading(reading,"明日の会議でつかうしりょうを確認してめーるでおくってください",active))
        assertFalse(engine.matchesReading(reading,"明日の会議で使う資料を確認してください",active))
        assertFalse(engine.matchesReading("きょう","今日の天気",active))
        assertFalse(engine.matchesReading("きょう","今日",java.util.function.BooleanSupplier{true}))
    }
    @Test fun longSentencesConvertWithoutDroppingMixedLiteralText() {
        val engine=KanaKanjiEngine()
        engine.buildEngineFromResourceDirectory(System.getProperty("sumire.dictionary"),1)
        for((reading,expected) in listOf(
            "きょうはいいてんきなのでこうえんにさんぽにいきます" to "今日はいい天気なので公園に散歩に行きます",
            "あしたのかいぎでつかうしりょうをかくにんしてめーるでおくってください" to "明日の会議で使う資料を確認してメールで送ってください",
            "このきーぼーどはにほんごのへんかんこうほをすばやくひょうじします" to "このキーボードは日本語の変換候補を素早く表示します"
        )) {
            val candidates=engine.nBestPath(reading,12)
            assertTrue(reading,candidates.isNotEmpty())
            assertEquals("cheapest full path must lead",engine.viterbiAlgorithm(reading),candidates.first())
            assertEquals(expected,candidates.first())
        }
        assertTrue(engine.nBestPath("にほん😀",4).contains("日本😀"))
        val mixed=net.neomenia.inputcore.ConversionPlan.convert("にほん😀 https://example.com",128,4,{text,count->engine.nBestPath(text,count)},{false})
        assertTrue(mixed.toString(),mixed.any{it.startsWith("日本")&&it.endsWith("😀 https://example.com")})
        val reading="きょうはいいてんきです。".repeat(16)
        val paragraph=net.neomenia.inputcore.ConversionPlan.convert(reading,128,12,{text,count->engine.nBestPath(text,count)},{false})
        assertEquals("今日はいい天気です。".repeat(16),paragraph.first())
    }
    @Test fun cancelledSearchStopsAndNextReadingStillConverts() {
        val engine=KanaKanjiEngine()
        engine.buildEngineFromResourceDirectory(System.getProperty("sumire.dictionary"),1)
        var checks=0
        assertTrue(engine.nBestPath("きょうはいいてんきです",12,java.util.function.BooleanSupplier { ++checks>12 }).isEmpty())
        assertTrue(checks>12)
        assertTrue(engine.nBestPath("にほん",12).contains("日本"))
        assertTrue(engine.nBestPath("がっこう",12,java.util.function.BooleanSupplier { true }).isEmpty())
    }
    @Test fun realBundledDictionaryConvertsKana() {
        val engine = KanaKanjiEngine()
        engine.buildEngineFromResourceDirectory(System.getProperty("sumire.dictionary"), 1)
        for ((reading, expected) in listOf("にほん" to "日本", "がっこう" to "学校", "きょう" to "今日")) {
            val candidates = engine.nBestPath(reading, 20)
            assertTrue("$reading: $candidates", candidates.contains(expected))
        }
        for (reading in listOf("しろ", "しろい", "しろくろ", "しろあり", "しろい")) {
            assertTrue("No candidate: $reading", engine.nBestPath(reading, 8).isNotEmpty())
        }
    }
}
