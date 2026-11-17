package dev.jdguo.market.ui;

/** GUI-scaled coordinates; compact screens use pages rather than squeezed trading columns. */
public final class MarketLayout {
    public record Box(int x,int y,int width,int height) {}
    public record Layout(Box listings,Box details,Box order,Box book,boolean compact,int bottom) {}
    private MarketLayout() {}
    public static Layout at(int width,int height) {
        int bottom=Math.max(63,height-36), usable=Math.max(1,width-16), h=bottom-62;
        if(width<800 || height<440) {
            Box full=new Box(8,62,usable,h);
            return new Layout(full,full,full,full,true,bottom);
        }
        int left=Math.max(170,usable/5),right=Math.max(180,usable/5),gap=6;
        int center=usable-left-right-2*gap, topHeight=h-158;
        return new Layout(new Box(8,62,left,h),new Box(8+left+gap,62,center,topHeight),
                new Box(8+left+gap,62+topHeight+gap,center+right+gap,152),
                new Box(width-8-right,62,right,topHeight),false,bottom);
    }
}
