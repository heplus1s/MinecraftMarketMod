package dev.jdguo.market.listing;

import dev.jdguo.market.api.SettlementResult;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Actual compressed-NBT disk round trips; run in the full Minecraft test classpath. */
class LandSaleSignalRecoveryTest {
    @TempDir Path directory;
    private Listing land(UUID seller) {
        return Listing.land(UUID.randomUUID(),ListingKind.PARCEL,seller,"Seller",Proceeds.SELLER,BigInteger.TEN,"note",UUID.randomUUID(),"Land","minecraft:overworld",List.of(new ChunkRef("minecraft:overworld",0,0)));
    }
    private void save(MarketBook book, Path file) {
        try { NbtIo.writeCompressed(book.write(),file.toFile()); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private MarketBook load(Path file) throws Exception { return MarketBook.read(NbtIo.readCompressed(file.toFile())); }
    @Test void successfulSaleAndSignalSurviveDiskReloadWithoutReplay() throws Exception {
        MarketBook book=new MarketBook();Listing listing=land(UUID.randomUUID());book.add(listing);Path file=directory.resolve("market.dat");Pay pay=new Pay();
        assertTrue(Checkout.buy(book,listing.id(),UUID.randomUUID(),"Buyer",BigInteger.TEN,pay,(l,b,n)->Handoff.Outcome.success(null),()->save(book,file)).ok());
        MarketBook loaded=load(file);assertEquals(1,loaded.landSaleSignals().size());assertTrue(loaded.deals().isEmpty());
        Checkout.recover(loaded,pay,(l,b,n)->{throw new AssertionError("completed sale replayed");},()->save(loaded,file),true);
        assertEquals(1,load(file).landSaleSignals().size());assertEquals(1,pay.pays);
    }
    @Test void failedFinalCheckpointRestartsFromPaidJournalAndRecordsOnce() throws Exception {
        MarketBook book=new MarketBook();Listing listing=land(UUID.randomUUID());book.add(listing);Path file=directory.resolve("market.dat");Pay pay=new Pay();
        AtomicInteger checkpoints=new AtomicInteger(), actualTransfers=new AtomicInteger();Set<UUID> transferred=new HashSet<>();
        Handoff transfer=(l,b,n)->{if(transferred.add(l.id()))actualTransfers.incrementAndGet();return Handoff.Outcome.success(null);};
        assertThrows(IllegalStateException.class,()->Checkout.buy(book,listing.id(),UUID.randomUUID(),"Buyer",BigInteger.TEN,pay,transfer,()->{
            if(checkpoints.incrementAndGet()==4) throw new IllegalStateException("injected disk failure before completed checkpoint");save(book,file);
        }));
        MarketBook loaded=load(file);assertTrue(loaded.landSaleSignals().isEmpty());assertEquals(Deal.Stage.PAID,loaded.deals().get(0).stage());
        Checkout.recover(loaded,pay,transfer,()->save(loaded,file),true);
        MarketBook again=load(file);assertEquals(1,again.landSaleSignals().size());assertTrue(again.deals().isEmpty());assertEquals(1,pay.pays);assertEquals(1,actualTransfers.get());
    }
    @Test void rejectedTransferRefundsAndProducesNoSaleSignal() throws Exception {
        MarketBook book=new MarketBook();Listing listing=land(UUID.randomUUID());book.add(listing);Path file=directory.resolve("market.dat");Pay pay=new Pay();
        assertFalse(Checkout.buy(book,listing.id(),UUID.randomUUID(),"Buyer",BigInteger.TEN,pay,(l,b,n)->Handoff.Outcome.drop("stale"),()->save(book,file)).ok());
        assertTrue(load(file).landSaleSignals().isEmpty());assertEquals(1,pay.refunds);
    }
    private static final class Pay implements Payments {
        final Set<String> receipts=new HashSet<>();int pays,refunds;
        public SettlementResult receipt(String r){return receipts.contains(r)?SettlementResult.ok():SettlementResult.fail("unrecorded","");}
        public SettlementResult paySeller(UUID a,UUID b,String c,BigInteger n,String r){if(receipts.add(r))pays++;return SettlementResult.ok();}
        public SettlementResult refundSeller(UUID a,UUID b,String c,BigInteger n,String r){if(receipts.add(r))refunds++;return SettlementResult.ok();}
        public SettlementResult payLandGrant(UUID a,String c,BigInteger n,String r){throw new AssertionError();}
        public SettlementResult refundLandGrant(UUID a,String c,BigInteger n,String r){throw new AssertionError();}
    }
}
