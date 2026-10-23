package com.kazumaproject.viterbi

import com.kazumaproject.graph.Node
import com.kazumaproject.mozc.ConnectionMatrix
import com.kazumaproject.ngram.EmptyStandaloneUnigramDictionary
import com.kazumaproject.ngram.StandaloneUnigramDictionary
import com.kazumaproject.ngram.EmptySystemNgramDictionary
import com.kazumaproject.ngram.EmptySystemUnigramDictionary
import com.kazumaproject.ngram.SystemNgramDictionary
import com.kazumaproject.ngram.SystemUnigramDictionary
import java.util.PriorityQueue
import java.util.function.BooleanSupplier
import kotlin.math.sqrt

class FindPath(
    private val systemNgramDictionary: SystemNgramDictionary = EmptySystemNgramDictionary,
    private val systemUnigramDictionary: SystemUnigramDictionary = EmptySystemUnigramDictionary,
    private val standaloneUnigramDictionary: StandaloneUnigramDictionary = EmptyStandaloneUnigramDictionary,
) {

    private data class PathState(
        val node: Node,
        val path: List<Node>,
        val cost: Int,
    )

    private data class CandidatePath(
        val value: String,
        val cost: Int,
        val matchedBySystemDictionary: Boolean,
    )

    fun viterbi(
        graph: List<MutableList<MutableList<Node>>>,
        length: Int,
        connectionIds: ShortArray
    ): String = viterbi(graph, length, inferConnectionMatrix(connectionIds))

    fun viterbi(
        graph: List<MutableList<MutableList<Node>>>,
        length: Int,
        connectionMatrix: ConnectionMatrix
    ): String {
        return findBestPath(graph, length, connectionMatrix).joinToString(separator = "") { it.tango }
    }

    fun findBestPath(
        graph: List<MutableList<MutableList<Node>>>,
        length: Int,
        connectionIds: ShortArray
    ): List<Node> = findBestPath(graph, length, inferConnectionMatrix(connectionIds))

    fun findBestPath(
        graph: List<MutableList<MutableList<Node>>>,
        length: Int,
        connectionMatrix: ConnectionMatrix
    ): List<Node> {
        buildViterbi(graph, length, connectionMatrix)
        val result = mutableListOf<Node>()
        var node = graph[length + 1].flatten().firstOrNull { it.tango == "EOS" }?.prev
        while (node != null && node.tango != "BOS") {
            result.add(node)
            node = node.prev
        }
        return result.asReversed()
    }

    fun backwardAStar(
        graph: List<MutableList<MutableList<Node>>>,
        length: Int,
        connectionIds: ShortArray,
        n: Int
    ): MutableList<String> = backwardAStar(graph, length, inferConnectionMatrix(connectionIds), n)

    fun backwardAStar(
        graph: List<MutableList<MutableList<Node>>>,
        length: Int,
        connectionMatrix: ConnectionMatrix,
        n: Int,
        input: String? = null,
        cancelled: BooleanSupplier = BooleanSupplier { false },
    ): MutableList<String> {
        if (n <= 0) return mutableListOf()
        // Word/connection costs may be negative. Prefix-only uniform-cost search can
        // return an expensive complete sentence before a cheaper unfinished path.
        // Viterbi prefix minima are exact A* lower bounds on this acyclic lattice.
        if (!buildViterbi(graph, length, connectionMatrix, cancelled)) return mutableListOf()
        val queue = PriorityQueue(compareBy<PathState> { it.cost.toLong() + it.node.totalCost })
        val eos = graph[length + 1].flatten().firstOrNull { it.tango == "EOS" } ?: return mutableListOf()
        if (eos.totalCost == Int.MAX_VALUE) return mutableListOf()
        queue.add(PathState(node = eos, path = emptyList(), cost = 0))

        val canMatchStandalone = input != null && input.length == length &&
            standaloneUnigramDictionary.mayMatchInput(input)
        val hasSystemRules =
            systemNgramDictionary.ruleCount > 0 || systemUnigramDictionary.ruleCount > 0 ||
                canMatchStandalone
        val internalCandidateCount = if (hasSystemRules) {
            maxOf(n, n.saturatingMultiply(4).coerceAtMost(64), 32)
        } else {
            n
        }
        val resultFinal = mutableListOf<CandidatePath>()
        val candidateIndices = HashMap<String, Int>()

        while (queue.isNotEmpty()) {
            if(cancelled.asBoolean)return mutableListOf()
            val state = queue.poll()
            if (state.node.tango == "BOS") {
                val value = state.path.joinToString(separator = "") { it.tango }
                val standaloneMatch = canMatchStandalone &&
                    standaloneUnigramDictionary.matches(requireNotNull(input), state.path)
                val existingIndex = candidateIndices[value]
                if (existingIndex == null) {
                    candidateIndices[value] = resultFinal.size
                    resultFinal += CandidatePath(
                        value = value,
                        cost = state.cost,
                        matchedBySystemDictionary =
                            pathMatchesSystemNgram(state.path) || pathMatchesSystemUnigram(state.path) ||
                                standaloneMatch,
                    )
                } else if (standaloneMatch) {
                    // v5 can establish whole-input eligibility on a later one-node path.
                    // Keep the first path's cost and v3/v4's first-distinct-path semantics.
                    resultFinal[existingIndex] = resultFinal[existingIndex].copy(matchedBySystemDictionary = true)
                }
                if (
                    resultFinal.size >= internalCandidateCount ||
                    (resultFinal.size >= n && resultFinal.any { it.matchedBySystemDictionary })
                ) break
                continue
            }

            val position = if (state.node.tango == "EOS") length else state.node.sPos
            for (previous in graph[position].flatten()) {
                if (previous.totalCost == Int.MAX_VALUE) continue
                val edgeScore = getEdgeCost(previous.r.toInt(), state.node.l.toInt(), connectionMatrix)
                val cost = addCosts(state.cost, edgeScore, state.node.wcost)
                val path = if (previous.tango == "BOS") state.path else listOf(previous) + state.path
                queue.add(PathState(node = previous, path = path, cost = cost))
            }
        }
        return resultFinal
            .sortedWith(
                compareByDescending<CandidatePath> { it.matchedBySystemDictionary }
                    .thenBy { it.cost },
            )
            .take(n)
            .mapTo(mutableListOf()) { it.value }
    }

    private fun pathMatchesSystemNgram(path: List<Node>): Boolean {
        if (systemNgramDictionary.ruleCount == 0) return false
        for (start in path.indices) {
            val secondIndex = start + 1
            if (secondIndex >= path.size) break
            if (
                systemNgramDictionary.matches(
                    node0 = path[start],
                    node1 = path[secondIndex],
                    node2 = path.getOrNull(start + 2),
                    node3 = path.getOrNull(start + 3),
                    node4 = path.getOrNull(start + 4),
                )
            ) return true
        }
        return false
    }

    // JapaneseKeyboard checks matchesSingleNode at every node in the path,
    // after retaining the first path for each distinct candidate surface.
    private fun pathMatchesSystemUnigram(path: List<Node>): Boolean =
        systemUnigramDictionary.ruleCount > 0 && path.any(systemUnigramDictionary::matches)

    private fun buildViterbi(
        graph: List<MutableList<MutableList<Node>>>,
        length: Int,
        connectionMatrix: ConnectionMatrix,
        cancelled: BooleanSupplier = BooleanSupplier { false },
    ): Boolean {
        resetScores(graph)
        for (i in 1 .. length + 1){
            val nodes = graph[i].flatten()
            for (node in nodes){
                if (cancelled.asBoolean) return false
                var cost = Int.MAX_VALUE
                var shortestPrev: Node? = null
                val prevNodes = getPrevNodesForViterbi(
                    graph,
                    node,
                    i,
                ).flatten()
                for (prevNode in prevNodes){
                    if (prevNode.totalCost == Int.MAX_VALUE) continue
                    val edgeCost = getEdgeCost(
                        prevNode.r.toInt(),
                        node.l.toInt(),
                        connectionMatrix
                    )
                    val tempCost = addCosts(prevNode.totalCost, node.wcost, edgeCost)
                    if (tempCost < cost){
                        cost = tempCost
                        shortestPrev = prevNode
                    }
                }
                node.score = cost
                node.totalCost = cost
                node.prev = shortestPrev
            }
        }
        return true
    }

    private fun getPrevNodesForViterbi(
        graph: List<MutableList<MutableList<Node>>>,
        node: Node,
        startPosition: Int,
    ): MutableList<MutableList<Node>>{
        val index = if (node.tango == "EOS") startPosition - 1 else node.sPos
        if (index < 0 || index >= graph.size) return mutableListOf()
        return graph[index]
    }

    private fun getEdgeCost(
        leftId: Int,
        rightId: Int,
        connectionMatrix: ConnectionMatrix
    ):Int {
        return connectionMatrix.getCost(leftId, rightId)
    }

    private fun inferConnectionMatrix(connectionIds: ShortArray): ConnectionMatrix {
        val size = sqrt(connectionIds.size.toDouble()).toInt()
        require(size * size == connectionIds.size) {
            "Invalid connection ID array: short count=${connectionIds.size}, reason=short count must be a perfect square"
        }
        return ConnectionMatrix(size, connectionIds)
    }

    private fun resetScores(graph: List<MutableList<MutableList<Node>>>) {
        graph.forEach { groups ->
            groups.flatten().forEach { node ->
                node.prev = null
                node.next = null
                node.g = 0
                if (node.tango == "BOS") {
                    node.score = 0
                    node.f = 0
                    node.totalCost = 0
                } else {
                    node.score = node.wcost
                    node.f = Int.MAX_VALUE
                    node.totalCost = Int.MAX_VALUE
                }
            }
        }
    }

    private fun addCosts(vararg costs: Int): Int {
        val total = costs.fold(0L) { acc, cost -> acc + cost.toLong() }
        return total.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    private fun Int.saturatingMultiply(multiplier: Int): Int =
        if (this > Int.MAX_VALUE / multiplier) Int.MAX_VALUE else this * multiplier

}
