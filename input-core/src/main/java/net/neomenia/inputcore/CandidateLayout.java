package net.neomenia.inputcore;

import java.util.*;

/** Candidates keep rank order from left to right, then continue on the next row. */
public final class CandidateLayout {
    public record Cell(int index,int x,int y,int width) {}
    public record Result(List<Cell> cells,int width,int height) {}
    public static Result pack(int viewport,int rowHeight,int[] widths,boolean wrap){
        viewport=Math.max(1,viewport);rowHeight=Math.max(1,rowHeight);
        List<Cell> cells=new ArrayList<>();int x=0,y=0,maxWidth=0;
        for(int index=0;index<widths.length;index++){
            int width=Math.max(1,Math.min(viewport,widths[index]));
            if(wrap&&x>0&&x+width>viewport){x=0;y+=rowHeight;}
            cells.add(new Cell(index,x,y,width));x+=width;maxWidth=Math.max(maxWidth,x);
        }
        // Share each row's unused width equally between its candidate cells.
        for(int start=0;start<cells.size();){
            int end=start+1;while(end<cells.size()&&cells.get(end).y()==cells.get(start).y())end++;
            Cell last=cells.get(end-1);int spare=Math.max(0,viewport-last.x()-last.width()),count=end-start,left=0;
            for(int index=start;index<end;index++){
                Cell cell=cells.get(index);int offset=index-start;
                int extra=spare/count+(offset<spare%count?1:0);
                cells.set(index,new Cell(cell.index(),left,cell.y(),cell.width()+extra));left+=cell.width()+extra;
            }
            start=end;
        }
        return new Result(cells,wrap?viewport:cells.isEmpty()?0:Math.max(viewport,maxWidth),cells.isEmpty()?0:y+rowHeight);
    }
}
