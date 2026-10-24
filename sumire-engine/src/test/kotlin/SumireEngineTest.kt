import com.kazumaproject.engine.KanaKanjiEngine
import org.junit.Assert.*
import org.junit.Test

class SumireEngineTest {
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
