package dev.jdguo.market.listing;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NearbyLandSignalsTest {
    @Test void perSellerMaxNotSumAndNoCrossDimension() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        var index=new NearbyLandSignals(List.of(new NearbyLandSignals.Point(new ChunkRef("a",-1,0),a,3000),new NearbyLandSignals.Point(new ChunkRef("a",1,0),a,8000),new NearbyLandSignals.Point(new ChunkRef("b",0,0),b,10000)),8);
        assertEquals(8000,index.weight(new ChunkRef("a",0,0)));assertEquals(0,index.weight(new ChunkRef("a",100,100)));
    }
    @Test void randomIndexMatchesBruteForce() {
        Random r=new Random(91);List<NearbyLandSignals.Point> points=new ArrayList<>();UUID[] ids={UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()};
        for(int i=0;i<300;i++)points.add(new NearbyLandSignals.Point(new ChunkRef("a",r.nextInt(100)-50,r.nextInt(100)-50),ids[r.nextInt(3)],r.nextInt(10001)));
        for(int radius:new int[]{0,1,8,128}) {
            var index=new NearbyLandSignals(points,radius);
            for(int i=0;i<100;i++) {
                ChunkRef c=new ChunkRef("a",r.nextInt(200)-100,r.nextInt(200)-100);Map<UUID,Long> expected=new HashMap<>();
                for(var p:points)if(LandPriceMath.nearby(c,p.chunk(),radius))expected.merge(p.seller(),p.weight(),Math::max);
                assertEquals(expected.values().stream().mapToLong(Long::longValue).sum(),index.weight(c));
                assertEquals(expected.values().stream().anyMatch(w->w>0),index.any(c));
            }
        }
    }
}
