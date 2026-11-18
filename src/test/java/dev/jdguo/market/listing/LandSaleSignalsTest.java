package dev.jdguo.market.listing;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LandSaleSignalsTest {
    private final ChunkRef a = new ChunkRef("overworld",1,2), b = new ChunkRef("overworld",1,3);
    @Test void repeatedTradesCannotMultiplyOrRefreshSameChunk() {
        var book=new LandSaleSignals(); UUID seller=UUID.randomUUID();
        book.record(List.of(a,a,b),seller,1000);book.record(List.of(a,b),UUID.randomUUID(),2000);
        assertEquals(2,book.all().size());assertTrue(book.all().stream().allMatch(s->s.seller().equals(seller)&&s.time()==1000));
    }
    @Test void eligibleAgainOnlyAfterCooldown() {
        var book=new LandSaleSignals(); book.record(List.of(a),UUID.randomUUID(),0);UUID later=UUID.randomUUID();
        book.record(List.of(a),later,LandSaleSignals.RETENTION_MILLIS);
        assertEquals(1,book.all().size());assertEquals(later,book.all().get(0).seller());
    }
    @Test void restoredSignalsRejectInvalidFutureExpiredData() {
        var book=new LandSaleSignals();UUID seller=UUID.randomUUID();long now=LandSaleSignals.RETENTION_MILLIS+100;
        book.restore(a,seller,0,now);book.restore(a,seller,now+1,now);book.restore(a,seller,-1,now);assertTrue(book.all().isEmpty());
        book.restore(a,seller,now-100,now);book.restore(a,UUID.randomUUID(),now,now);
        assertEquals(1,book.all().size());assertEquals(now-100,book.all().get(0).time());
    }
    @Test void dimensionsAreIndependent() {
        var book=new LandSaleSignals();book.record(List.of(a,new ChunkRef("nether",1,2)),UUID.randomUUID(),10);assertEquals(2,book.all().size());
    }
}
