package net.neomenia.inputcore;

import java.util.*;

/** Candidates keep rank order from left to right, then continue on the next row. */
public final class CandidateLayout {
    public record Cell(int index,int x,int y,int width) {}
    public record Result(List<Cell> cells,int width,int height) {}
    public static Result pack(int viewport,int rowHeight,int[] widths,boolean wrap){
        return pack(viewport,rowHeight,widths,wrap,0);
    }
    /** Reserve space for the expand control on the first row only. */
    public static Result pack(int viewport,int rowHeight,int[] widths,boolean wrap,int firstRowInset){
        viewport=Math.max(1,viewport);rowHeight=Math.max(1,rowHeight);
        int firstRowWidth=Math.max(1,viewport-Math.max(0,firstRowInset));
        List<Cell> cells=new ArrayList<>();int x=0,y=0,maxWidth=0;
        for(int index=0;index<widths.length;index++){
            int available=y==0?firstRowWidth:viewport;
            int width=Math.max(1,Math.min(available,widths[index]));
            if(wrap&&x>0&&x+width>available){x=0;y+=rowHeight;width=Math.max(1,Math.min(viewport,widths[index]));}
            cells.add(new Cell(index,x,y,width));x+=width;maxWidth=Math.max(maxWidth,x);
        }
        // Share each row's unused width equally between its candidate cells.
        for(int start=0;start<cells.size();){
            int end=start+1;while(end<cells.size()&&cells.get(end).y()==cells.get(start).y())end++;
            Cell last=cells.get(end-1);int available=last.y()==0?firstRowWidth:viewport;
            int spare=Math.max(0,available-last.x()-last.width()),count=end-start,left=0;
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
