package com.kazumaproject.graph

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.Other.BOS
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import java.util.concurrent.CancellationException
import java.util.function.BooleanSupplier

class GraphBuilder {

    fun constructGraph(
        str: String,
        yomiTrie: LOUDSWithTermId,
        tangoTrie: LOUDS,
        tokenArray: TokenArray,
        cancelled: BooleanSupplier = BooleanSupplier { false }
    ): List<MutableList<MutableList<Node>>> {
        val graph: MutableList<MutableList<MutableList<Node>>> =
            MutableList(str.length + 2) { mutableListOf() }
        graph[0].add(
            mutableListOf(
                BOS.copy(
                    score = 0,
                    f = 0,
                    g = 0,
                    totalCost = 0,
                    prev = null,
                    next = null,
                )
            )
        )
        graph[str.length + 1].add(
            mutableListOf(
                Node(
                    l = 0,
                    r = 0,
                    score = 0,
                    f = 0,
                    g = 0,
                    tango = "EOS",
                    len = 0,
                    sPos = str.length,
                    key = "EOS",
                    wcost = 0,
                    totalCost = Int.MAX_VALUE,
                )
            )
        )

        for (i in str.indices){
            if(cancelled.asBoolean)throw CancellationException()
            if (i > 0 && Character.isLowSurrogate(str[i]) && Character.isHighSurrogate(str[i-1])) continue
            val subStr = str.substring(i, str.length)
            val commonPrefixSearch = yomiTrie.commonPrefixSearch(subStr)
            val fallbackLength=Character.charCount(str.codePointAt(i))
            if (commonPrefixSearch.none { it.length == fallbackLength }) {
                val literal=str.substring(i,i+fallbackLength)
                graph[i+fallbackLength].add(mutableListOf(Node(l=0,r=0,score=12000,f=12000,tango=literal,len=fallbackLength.toShort(),sPos=i,key=literal,wcost=12000)))
            }
            commonPrefixSearch.forEach { yomiStr ->
                if(cancelled.asBoolean)throw CancellationException()
                val termId = yomiTrie.getTermId(yomiTrie.getNodeIndex(yomiStr))
                val listToken = tokenArray.getListDictionaryByYomiTermId(termId)
                val tangoList = listToken.map {
                    if(cancelled.asBoolean)throw CancellationException()
                    val value=when(it.nodeId){
                        -2 -> yomiStr
                        -1 -> yomiStr.hiraToKata()
                        else -> tangoTrie.getLetter(it.nodeId)
                    }
                    // Symbol readings are useful alone, but must not beat words inside a sentence.
                    val embeddedSymbol=(i!=0||yomiStr.length!=str.length)&&
                        yomiStr.codePoints().allMatch { cp ->
                            val script=Character.UnicodeScript.of(cp)
                            script==Character.UnicodeScript.HIRAGANA||script==Character.UnicodeScript.KATAKANA||cp==0x30fc
                        }&&value.isNotBlank()&&value.codePoints().noneMatch(Character::isLetterOrDigit)
                    val cost=it.wordCost.toInt()+if(embeddedSymbol)24000 else 0
                    Node(
                        l = tokenArray.leftIds[it.posTableIndex.toInt()],
                        r = tokenArray.rightIds[it.posTableIndex.toInt()],
                        score = cost,
                        f = cost,
                        g = cost,
                        tango = value,
                        len = yomiStr.length.toShort(),
                        sPos = i,
                        key = yomiStr,
                        wcost = cost,
                        totalCost = Int.MAX_VALUE,
                    )
                }
                graph[i + yomiStr.length].add(tangoList.toMutableList())
            }
        }
        return graph
    }

}
