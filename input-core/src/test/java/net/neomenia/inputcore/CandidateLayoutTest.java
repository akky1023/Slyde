package net.neomenia.inputcore;

import org.junit.Test;
import static org.junit.Assert.*;

public class CandidateLayoutTest {
    @Test public void expandControlReservesOnlyTheFirstWrappedRow(){
        var layout=CandidateLayout.pack(300,44,new int[]{100,100,100,100,100},true,44);
        assertEquals(300,layout.width());assertEquals(88,layout.height());
        assertEquals(256,layout.cells().get(1).x()+layout.cells().get(1).width());
        assertEquals(44,layout.cells().get(2).y());
        assertEquals(100,layout.cells().get(2).width());
        assertEquals(300,layout.cells().get(4).x()+layout.cells().get(4).width());
        var longCells=CandidateLayout.pack(300,44,new int[]{500,500},true,44);
        assertEquals(256,longCells.cells().get(0).width());
        assertEquals(300,longCells.cells().get(1).width());
    }
    @Test public void wrapsInRankOrderAtTheAvailableWidth(){
        var layout=CandidateLayout.pack(300,44,new int[]{80,100,90,150,150,80},true);
        assertEquals(132,layout.height());
        assertEquals(0,layout.cells().get(0).x());assertEquals(90,layout.cells().get(1).x());assertEquals(200,layout.cells().get(2).x());
        assertEquals(0,layout.cells().get(3).x());assertEquals(44,layout.cells().get(3).y());
        assertEquals(150,layout.cells().get(4).x());assertEquals(88,layout.cells().get(5).y());
        assertEquals(300,layout.cells().get(5).width());
    }
    @Test public void shortRowsShareSpaceAndKeepEveryPixel(){
        for(boolean wrap:new boolean[]{false,true}){
            var layout=CandidateLayout.pack(301,30,new int[]{60,80,40},wrap);
            assertEquals(301,layout.width());
            assertEquals(101,layout.cells().get(0).width());
            assertEquals(120,layout.cells().get(1).width());
            assertEquals(80,layout.cells().get(2).width());
            assertEquals(221,layout.cells().get(2).x());
            var single=CandidateLayout.pack(301,30,new int[]{60},wrap);
            assertEquals(301,single.cells().get(0).width());
        }
    }
    @Test public void oneRowScrollsHorizontallyAndLongCandidatesStayReachable(){
        var layout=CandidateLayout.pack(200,44,new int[]{70,500,80},false);
        assertEquals(44,layout.height());assertEquals(350,layout.width());assertEquals(200,layout.cells().get(1).width());assertEquals(270,layout.cells().get(2).x());
    }
    @Test public void resizingRepacksWithoutDroppingCandidates(){
        int[] widths={100,100,100,100};
        assertEquals(4,CandidateLayout.pack(150,44,widths,true).cells().size());
        assertEquals(176,CandidateLayout.pack(150,44,widths,true).height());
        assertEquals(44,CandidateLayout.pack(400,44,widths,true).height());
        assertEquals(0,CandidateLayout.pack(300,44,new int[]{},true).height());
    }
}
