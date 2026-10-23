import com.kazumaproject.graph.Node
import com.kazumaproject.mozc.ConnectionMatrix
import com.kazumaproject.viterbi.FindPath
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class FindPathRankingTest {
    private fun node(text:String,start:Int,len:Int,cost:Int,l:Int=0,r:Int=0)=Node(l.toShort(),r.toShort(),cost,cost,tango=text,len=len.toShort(),sPos=start,wcost=cost)
    private fun graph(length:Int,nodes:List<Node>)=List(length+2){mutableListOf<MutableList<Node>>()}.also { g->
        g[0].add(mutableListOf(node("BOS",0,0,0)));g[length+1].add(mutableListOf(node("EOS",length,0,0)))
        nodes.forEach { g[it.sPos+it.len].add(mutableListOf(it)) }
    }
    @Test fun negativeLaterWordMustBeatAnEarlyCompletePath(){
        val g=graph(2,listOf(node("誤候補",0,2,5),node("正",0,1,20),node("解",1,1,-100)))
        assertEquals(listOf("正解"),FindPath().backwardAStar(g,2,ConnectionMatrix(1,shortArrayOf(0)),1))
    }
    @Test fun negativeConnectionCostIsIncludedBeforeChoosingTheWinner(){
        val g=graph(2,listOf(node("誤候補",0,2,0),node("正",0,1,20,r=1),node("解",1,1,5,l=1)))
        assertEquals(listOf("正解"),FindPath().backwardAStar(g,2,ConnectionMatrix(2,shortArrayOf(0,0,0,-100)),1))
    }
    @Test fun nBestCostsMatchExhaustiveSearchOnSmallLattices(){
        val random=Random(42);val matrix=ConnectionMatrix(2,shortArrayOf(0,-3,2,-8))
        repeat(40){
            val length=5;val nodes=mutableListOf<Node>()
            for(start in 0 until length)for(len in 1..minOf(2,length-start))repeat(2){ variant->
                nodes+=node("[$start:$len:$variant]",start,len,random.nextInt(41)-20,random.nextInt(2),random.nextInt(2))
            }
            val all=mutableMapOf<String,Int>()
            fun visit(at:Int,previous:Int,text:String,cost:Int){
                if(at==length){all[text]=cost+matrix.getCost(previous,0);return}
                for(n in nodes.filter{it.sPos==at})visit(at+n.len,n.r.toInt(),text+n.tango,cost+n.wcost+matrix.getCost(previous,n.l.toInt()))
            }
            visit(0,0,"",0)
            val actual=FindPath().backwardAStar(graph(length,nodes),length,matrix,5)
            assertEquals(5,actual.distinct().size)
            assertEquals(all.values.sorted().take(5),actual.map{all.getValue(it)})
        }
    }
}
