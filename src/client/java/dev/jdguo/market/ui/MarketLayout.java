package dev.jdguo.market.ui;

/** Three simultaneous spot-trading columns in GUI-scaled pixels, never separate pages. */
public final class MarketLayout {
    public static final int MIN_WIDTH = 480, MIN_HEIGHT = 300;
    public record Box(int x,int y,int width,int height) {}
    public record Layout(Box listings,Box details,Box order,Box book,boolean compact,int bottom) {}
    private MarketLayout() {}
    public static boolean supported(int width,int height) { return width >= MIN_WIDTH && height >= MIN_HEIGHT; }
    public static Layout at(int width,int height) {
        int w=Math.max(MIN_WIDTH,width),h=Math.max(MIN_HEIGHT,height);
        int margin=6,gap=5,top=30,bottom=h-22,usable=w-margin*2;
        int left=Math.max(100,Math.min(200,usable/5));
        int right=Math.max(120,Math.min(220,usable/4));
        int center=usable-left-right-gap*2;
        int orderHeight=132, chartHeight=bottom-top-orderHeight-gap;
        return new Layout(new Box(margin,top,left,bottom-top),
            new Box(margin+left+gap,top,center,chartHeight),
            new Box(margin+left+gap,top+chartHeight+gap,center+right+gap,orderHeight),
            new Box(w-margin-right,top,right,chartHeight),false,bottom);
    }
}
