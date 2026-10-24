package com.kazumaproject.engine

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.graph.GraphBuilder
import com.kazumaproject.mozc.ConnectionMatrix
import com.kazumaproject.ngram.EmptyStandaloneUnigramDictionary
import com.kazumaproject.ngram.StandaloneUnigramDictionary
import com.kazumaproject.ngram.EmptySystemNgramDictionary
import com.kazumaproject.ngram.EmptySystemUnigramDictionary
import com.kazumaproject.ngram.SystemNgramDictionary
import com.kazumaproject.ngram.SystemUnigramDictionary
import com.kazumaproject.viterbi.FindPath
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.util.concurrent.CancellationException
import java.util.function.BooleanSupplier

class KanaKanjiEngine(
    private val systemNgramDictionary: SystemNgramDictionary = EmptySystemNgramDictionary,
    private val systemUnigramDictionary: SystemUnigramDictionary = EmptySystemUnigramDictionary,
    private val standaloneUnigramDictionary: StandaloneUnigramDictionary = EmptyStandaloneUnigramDictionary,
) {

    private lateinit var graphBuilder: GraphBuilder
    private lateinit var yomiTrie: LOUDSWithTermId
    private lateinit var tangoTrie: LOUDS
    private lateinit var connectionMatrix: ConnectionMatrix
    private lateinit var findPath: FindPath
    private lateinit var tokenArray: TokenArray

    fun buildEngine(){
        buildEngineFromResourceDirectory("src/main/resources", mode = 1)
    }

    fun buildEngineForTest(){
        val testResources = "src/test/resources"
        val hasTestResources = listOf(
            "yomi.dat",
            "tango.dat",
            "token.dat",
            "connectionId.dat",
            "pos_table.dat",
        ).all { File("$testResources/$it").isFile }
        if (hasTestResources) {
            buildEngineFromResourceDirectory(testResources, mode = 0)
        } else {
            buildEngine()
        }
    }

    fun nBestPath(
        input: String,
        n: Int
    ): List<String> = nBestPath(input,n,BooleanSupplier { false })

    fun nBestPath(input: String,n: Int,cancelled: BooleanSupplier): List<String> {
        if(cancelled.asBoolean)return emptyList()
        return try {
            val graph=graphBuilder.constructGraph(input,yomiTrie,tangoTrie,tokenArray,cancelled)
            findPath.backwardAStar(graph,input.length,connectionMatrix,n,input,cancelled)
        } catch (_: CancellationException) { emptyList() }
    }

    /** Include every whole-reading dictionary word even when sentence paths fill the N-best list. */
    fun candidates(input: String,n: Int,cancelled: BooleanSupplier): List<String> {
        if(input.isEmpty()||cancelled.asBoolean)return emptyList()
        return try {
            val graph=graphBuilder.constructGraph(input,yomiTrie,tangoTrie,tokenArray,cancelled)
            val ranked=findPath.backwardAStar(graph,input.length,connectionMatrix,n,input,cancelled,4096,40_000_000L)
            if(cancelled.asBoolean)return emptyList()
            val exact=graph[input.length].flatten().filter { it.sPos==0 && it.len.toInt()==input.length }
                .sortedBy { it.wcost }.map { it.tango }
            (ranked+exact).distinct()
        } catch (_: CancellationException) { emptyList() }
    }

    /** Check that generated text covers the supplied reading, without omitted/added words. */
    fun matchesReading(input: String,value: String,cancelled: BooleanSupplier): Boolean {
        if(cancelled.asBoolean)return false
        return try {
            val graph=graphBuilder.constructGraph(input,yomiTrie,tangoTrie,tokenArray,cancelled)
            val outgoing=Array(input.length+1){mutableListOf<com.kazumaproject.graph.Node>()}
            for(end in 1..input.length)for(group in graph[end])for(node in group)outgoing[node.sPos].add(node)
            val offsets=Array(input.length+1){mutableSetOf<Int>()};offsets[0].add(0)
            for(position in input.indices){
                if(cancelled.asBoolean)return false
                val cp=input.codePointAt(position);val next=position+Character.charCount(cp)
                val literal=String(Character.toChars(cp))
                val katakana=if(cp in 0x3041..0x3096)String(Character.toChars(cp+0x60))else literal
                for(offset in offsets[position]){
                    if(value.startsWith(literal,offset))offsets[next].add(offset+literal.length)
                    if(value.startsWith(katakana,offset))offsets[next].add(offset+katakana.length)
                    for(node in outgoing[position])if(value.startsWith(node.tango,offset))offsets[position+node.len].add(offset+node.tango.length)
                }
            }
            value.length in offsets[input.length]
        } catch (_: CancellationException) { false }
    }

    fun convert(
        input: String
    ): ConversionResult {
        val graph = graphBuilder.constructGraph(
            input,
            yomiTrie,
            tangoTrie,
            tokenArray,
        )
        val bestPath = findPath.findBestPath(graph, input.length, connectionMatrix)
        return ConversionResult(
            input = input,
            bestPath = bestPath.map { it.toConversionPathNode() },
        )
    }

    fun viterbiAlgorithm(
        input: String
    ): String{
        return convert(input).value
    }

    fun buildEngineFromResourceDirectory(
        resourceDirectory: String,
        mode: Int,
    ) {
        val objectInputYomi = ObjectInputStream(BufferedInputStream(FileInputStream("$resourceDirectory/yomi.dat")))
        val objectInputTango = ObjectInputStream(BufferedInputStream(FileInputStream("$resourceDirectory/tango.dat")))
        val objectInputTokenArray =
            ObjectInputStream(BufferedInputStream(FileInputStream("$resourceDirectory/token.dat")))
        val objectInputConnectionId = BufferedInputStream(FileInputStream("$resourceDirectory/connectionId.dat"))

        yomiTrie = LOUDSWithTermId().readExternalNotCompress(objectInputYomi)
        tangoTrie = LOUDS().readExternalNotCompress(objectInputTango)
        graphBuilder = GraphBuilder()
        tokenArray = TokenArray()
        tokenArray.readExternalNotCompress(objectInputTokenArray)
        tokenArray.readPOSTable(mode, "$resourceDirectory/pos_table.dat")
        connectionMatrix = ConnectionIdBuilder().readMatrix(objectInputConnectionId, "$resourceDirectory/connectionId.dat")
        findPath = FindPath(systemNgramDictionary, systemUnigramDictionary, standaloneUnigramDictionary)
    }

}
