package dev.jdguo.market;

import dev.jdguo.market.api.SettlementResult;
import dev.jdguo.market.api.ShopPriceApi;
import com.mojang.brigadier.CommandDispatcher;
import dev.jdguo.market.command.MarketCommandNames;
import dev.jdguo.market.command.MarketCommands;
import dev.jdguo.market.config.MarketConfig;
import dev.jdguo.market.integrate.ShopPrices;
import dev.jdguo.market.listing.Checkout;
import dev.jdguo.market.listing.ChunkRef;
import dev.jdguo.market.listing.Handoff;
import dev.jdguo.market.listing.LandPermit;
import dev.jdguo.market.listing.Listing;
import dev.jdguo.market.listing.ListingGuard;
import dev.jdguo.market.listing.ListingKind;
import dev.jdguo.market.listing.ListingStatus;
import dev.jdguo.market.listing.MarketBook;
import dev.jdguo.market.listing.Payments;
import dev.jdguo.market.listing.PriceMode;
import dev.jdguo.market.listing.Proceeds;
import dev.jdguo.market.listing.SaleReference;
import dev.jdguo.market.service.MarketDesk;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketBehaviorTest {
    private static final UUID ALICE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final String OVERWORLD = "minecraft:overworld";

    @AfterEach
    void resetShop() {
        ShopPrices.resetForTests();
    }

    @Test
    void saveKeepsUnknownKeysAndDoesNotDropAListingItCannotRead() {
        Listing listing = item(ALICE, 4, 10);
        NbtCompound tag = listing.write();
        tag.putString("FutureNote", "keep-me");
        Listing reread = Listing.read(tag);
        assertEquals("keep-me", reread.extraString("FutureNote"));
        assertEquals("keep-me", reread.write().getString("FutureNote"));

        MarketBook book = new MarketBook();
        assertTrue(book.add(reread));
        NbtCompound root = book.write();
        root.putString("FutureRoot", "yes");
        NbtCompound bad = new NbtCompound();
        bad.putString("Kind", "later");
        NbtList listings = root.getList("listings", 10);
        listings.add(bad);
        root.put("listings", listings);

        MarketBook loaded = MarketBook.read(root);
        assertEquals(1, loaded.size());
        assertEquals(1, loaded.preservedListings());
        assertEquals("yes", loaded.extraString("FutureRoot"));
        assertEquals("keep-me", loaded.get(listing.id()).orElseThrow().extraString("FutureNote"));
        NbtCompound again = loaded.write();
        assertEquals("yes", again.getString("FutureRoot"));
        assertTrue(again.getList("listings", 10).toString().contains("later"));
    }

    @Test
    void shortIdAndFullIdBothResolve() {
        MarketBook book = new MarketBook();
        Listing first = item(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"), ALICE, 1, 3);
        Listing second = item(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002"), ALICE, 1, 4);
        book.add(first);
        book.add(second);
        assertTrue(book.find("aaaaaaaa").ambiguous());
        assertEquals(first.id(), book.find(first.id().toString()).listing().id());
        assertEquals(second.id(), book.find("aaaaaaaa000000000000000000000002").listing().id());
        assertNull(book.find("zzzz").listing());
    }

    @Test
    void listedParcelBlocksGiftsAndAllowsItsOwnSaleReference() {
        UUID parcel = UUID.randomUUID();
        ChunkRef here = new ChunkRef(OVERWORLD, 2, -3);
        ChunkRef other = new ChunkRef(OVERWORLD, 3, -3);
        Listing parcelSale = Listing.land(UUID.randomUUID(), ListingKind.PARCEL, ALICE, "Alice", Proceeds.SELLER,
                BigInteger.valueOf(50), "note", parcel, "家园", OVERWORLD, List.of(here, other));
        MarketBook book = new MarketBook();
        book.add(parcelSale);
        ListingGuard guard = new ListingGuard(book);

        assertFalse(guard.allowsParcelTransfer(parcel, ""));
        assertFalse(guard.allowsParcelTransfer(parcel, "sale:" + UUID.randomUUID()));
        assertTrue(guard.allowsParcelTransfer(parcel, SaleReference.of(parcelSale.id())));
        assertFalse(guard.allowsAbandon(parcel));
        assertFalse(guard.allowsJoin(parcel));
        assertFalse(guard.allowsMerge(parcel, UUID.randomUUID()));
        assertTrue(guard.allowsAbandon(UUID.randomUUID()));
        assertFalse(guard.allowsSplit(parcel, List.of(here)));
    }

    @Test
    void chunkSaleCanBeSplitOnlyWhileThatPurchaseIsInProgress() {
        UUID parcel = UUID.randomUUID();
        ChunkRef sold = new ChunkRef(OVERWORLD, 0, 0);
        ChunkRef kept = new ChunkRef(OVERWORLD, 1, 0);
        Listing sale = Listing.land(UUID.randomUUID(), ListingKind.CHUNKS, ALICE, "Alice", Proceeds.SELLER,
                BigInteger.valueOf(8), "note", parcel, "", OVERWORLD, List.of(sold));
        MarketBook book = new MarketBook();
        book.add(sale);
        ListingGuard guard = new ListingGuard(book);

        assertFalse(guard.allowsChunkTransfer(parcel, sold, ""));
        assertTrue(guard.allowsChunkTransfer(parcel, sold, SaleReference.of(sale.id())));
        assertFalse(guard.allowsChunkTransfer(parcel, kept, SaleReference.of(sale.id())));
        assertFalse(guard.allowsSplit(parcel, List.of(sold)));
        assertFalse(LandPermit.call(SaleReference.of(sale.id()), () -> guard.allowsSplit(parcel, List.of(sold, kept))));
        assertTrue(LandPermit.call(SaleReference.of(sale.id()), () -> guard.allowsSplit(parcel, List.of(sold))));
    }

    @Test
    void playerTradePaysTheSellerAndServerLandPaysTheLandGrantAccount() {
        MarketBook items = new MarketBook();
        Listing goods = item(ALICE, 4, 10);
        items.add(goods);
        FakePayments payments = new FakePayments();
        Checkout.Outcome bought = Checkout.buy(items, goods.id(), BOB, "Bob", goods.customTotal(), payments,
                (listing, buyer, name) -> Handoff.Outcome.success(null), () -> {});
        assertTrue(bought.ok());
        assertEquals(1, payments.sellerPays);
        assertEquals(0, payments.grantPays);
        assertTrue(items.get(goods.id()).isEmpty());

        MarketBook land = new MarketBook();
        Listing grant = Listing.land(UUID.randomUUID(), ListingKind.PARCEL, ALICE, "Alice", Proceeds.LAND_GRANT,
                BigInteger.valueOf(80), "note", UUID.randomUUID(), "出让", OVERWORLD, List.of(new ChunkRef(OVERWORLD, 1, 1)));
        land.add(grant);
        FakePayments grantPay = new FakePayments();
        Checkout.Outcome sold = Checkout.buy(land, grant.id(), BOB, "Bob", grant.customTotal(), grantPay,
                (listing, buyer, name) -> Handoff.Outcome.success(null), () -> {});
        assertTrue(sold.ok());
        assertEquals(0, grantPay.sellerPays);
        assertEquals(1, grantPay.grantPays);
        assertTrue(grantPay.lastReference.startsWith("market-pay:"));
    }

    @Test
    void failedPaymentDoesNotTakeTheGoodsAndFailedDeliveryRefunds() {
        MarketBook book = new MarketBook();
        Listing goods = item(ALICE, 2, 5);
        book.add(goods);
        FakePayments poor = new FakePayments();
        poor.failSeller = true;
        Checkout.Outcome denied = Checkout.buy(book, goods.id(), BOB, "Bob", goods.customTotal(), poor,
                (listing, buyer, name) -> Handoff.Outcome.success(null), () -> {});
        assertEquals("insufficient_funds", denied.code());
        assertEquals(ListingStatus.OPEN, book.get(goods.id()).orElseThrow().status());
        assertEquals(0, poor.refundSeller);

        FakePayments paid = new FakePayments();
        Checkout.Outcome blocked = Checkout.buy(book, goods.id(), BOB, "Bob", goods.customTotal(), paid,
                (listing, buyer, name) -> Handoff.Outcome.failure("gate"), () -> {});
        assertEquals("gate", blocked.code());
        assertEquals(1, paid.refundSeller);
        assertEquals(ListingStatus.OPEN, book.get(goods.id()).orElseThrow().status());

        FakePayments stuck = new FakePayments();
        stuck.failRefund = true;
        Checkout.Outcome held = Checkout.buy(book, goods.id(), BOB, "Bob", goods.customTotal(), stuck,
                (listing, buyer, name) -> Handoff.Outcome.failure("delivery"), () -> {});
        assertEquals("refund_failed", held.code());
        assertEquals(1, book.deals().size());
        assertEquals(ListingStatus.RESERVED, book.get(goods.id()).orElseThrow().status());
    }

    @Test
    void staleLandDropsTheListingAfterRefundAndAReservedDealIsReleasedOnRecovery() {
        MarketBook book = new MarketBook();
        Listing grant = Listing.land(UUID.randomUUID(), ListingKind.CHUNKS, ALICE, "Alice", Proceeds.LAND_GRANT,
                BigInteger.TEN, "note", UUID.randomUUID(), "", OVERWORLD, List.of(new ChunkRef(OVERWORLD, 4, 4)));
        book.add(grant);
        FakePayments payments = new FakePayments();
        Checkout.Outcome stale = Checkout.buy(book, grant.id(), BOB, "Bob", BigInteger.TEN, payments,
                (listing, buyer, name) -> Handoff.Outcome.drop("stale"), () -> {});
        assertEquals("stale", stale.code());
        assertEquals(1, payments.refundGrant);
        assertTrue(book.get(grant.id()).isEmpty());
        assertTrue(book.deals().isEmpty());

        MarketBook waiting = new MarketBook();
        Listing goods = item(ALICE, 1, 3);
        waiting.add(goods);
        waiting.reserve(goods.id(), BOB, "Bob", BigInteger.valueOf(3));
        FakePayments untouched = new FakePayments();
        untouched.failSeller = true;
        untouched.failRefund = true;
        Checkout.recover(waiting, untouched, (listing, buyer, name) -> {
            throw new AssertionError("reserved deal must not be delivered");
        }, () -> {});
        assertTrue(waiting.deals().isEmpty());
        assertEquals(ListingStatus.OPEN, waiting.get(goods.id()).orElseThrow().status());
    }

    @Test
    void startupLocksUnreceiptedItemDebitsAndRecognizesAlreadyPostedRefunds() {
        MarketBook book = new MarketBook();
        Listing goods = item(ALICE, 1, 3);
        book.add(goods);
        var deal = book.reserve(goods.id(), BOB, "Bob", BigInteger.valueOf(3));
        FakePayments payments = new FakePayments();
        payments.recorded = java.util.Set.of(deal.reference());
        MarketBook reloaded = MarketBook.read(book.write());
        Checkout.recover(reloaded, payments, (listing, buyer, name) -> Handoff.Outcome.success(null), () -> {});
        assertEquals(1, reloaded.deals().size());
        assertEquals(dev.jdguo.market.listing.Deal.Stage.PAID, reloaded.deals().get(0).stage());
        assertEquals(ListingStatus.RESERVED, reloaded.get(goods.id()).orElseThrow().status());
        assertEquals(0, payments.sellerPays);

        book.markPaid(deal.id());
        payments.recorded = java.util.Set.of(deal.reference(), deal.refundReference());
        Checkout.recover(book, payments, (listing, buyer, name) -> {
            throw new AssertionError("refunded order must not deliver");
        }, () -> {});
        assertTrue(book.deals().isEmpty());
        assertEquals(ListingStatus.OPEN, book.get(goods.id()).orElseThrow().status());
        assertEquals(0, payments.refundSeller);
        var retry = book.reserve(goods.id(), BOB, "Bob", BigInteger.valueOf(3));
        assertFalse(deal.reference().equals(retry.reference()));
    }

    @Test
    void missingReceiptServiceNeverReleasesPossiblyPaidDeal() {
        for (boolean paid : new boolean[]{false, true}) {
            MarketBook book = new MarketBook();
            Listing goods = item(ALICE, 1, 3);
            book.add(goods);
            var deal = book.reserve(goods.id(), BOB, "Bob", BigInteger.valueOf(3));
            if (paid) book.markPaid(deal.id());
            FakePayments payments = new FakePayments();
            payments.receiptUnavailable = true;
            Checkout.recover(book, payments, (listing, buyer, name) -> {
                throw new AssertionError("unavailable receipt must stop recovery");
            }, () -> {});
            assertEquals(1, book.deals().size());
            assertEquals(ListingStatus.RESERVED, book.get(goods.id()).orElseThrow().status());
            assertEquals(0, payments.refundSeller);
        }
    }

    @Test
    void handoffExceptionAfterOwnershipChangeDoesNotRefundAndCanRecover() {
        MarketBook book = new MarketBook();
        Listing land = Listing.land(UUID.randomUUID(), ListingKind.PARCEL, ALICE, "Alice", Proceeds.SELLER,
                BigInteger.TEN, "note", UUID.randomUUID(), "Home", OVERWORLD,
                List.of(new ChunkRef(OVERWORLD, 1, 1)));
        book.add(land);
        FakePayments payments = new FakePayments();
        boolean[] transferred = {false};
        Checkout.Outcome result = Checkout.buy(book, land.id(), BOB, "Bob", BigInteger.TEN, payments,
                (listing, buyer, name) -> {
                    transferred[0] = true;
                    throw new IllegalStateException("network sync failed after ownership changed");
                }, () -> {});
        assertEquals("delivery_pending", result.code());
        assertTrue(transferred[0]);
        assertEquals(0, payments.refundSeller);
        assertEquals(1, book.deals().size());
        var deal = book.deals().get(0);
        payments.recorded = java.util.Set.of(deal.reference());
        Checkout.recover(book, payments, (listing, buyer, name) -> Handoff.Outcome.success(null), () -> {});
        assertTrue(book.deals().isEmpty());
        assertEquals(1, payments.sellerPays);
        assertEquals(0, payments.refundSeller);
    }

    @Test
    void successfulHandoffIsFlushedBeforeRecoveryJournalIsRemoved() {
        MarketBook book = new MarketBook();
        Listing goods = item(ALICE, 1, 3); book.add(goods);
        java.util.List<String> events = new java.util.ArrayList<>();
        Checkout.buy(book, goods.id(), BOB, "Bob", BigInteger.valueOf(3), new FakePayments(),
                (listing, buyer, name) -> { events.add("handoff"); return Handoff.Outcome.success(null); },
                () -> events.add(book.deals().isEmpty() ? "complete" : book.deals().get(0).stage().name()));
        assertEquals(java.util.List.of("RESERVED", "PAID", "handoff", "PAID", "complete"), events);
    }

    @Test
    void referenceUsesWeightedCompletedStandardItemTradesInTheSameCurrency() {
        MarketBook book = new MarketBook();
        for (int index = 0; index < 3; index++) {
            int count = index == 1 ? 20 : 4;
            int price = 2 + index;
            Listing listing = item(UUID.randomUUID(), UUID.randomUUID(), count, price);
            book.add(listing);
            var deal = book.reserve(listing.id(), UUID.randomUUID(), "Buyer", BigInteger.valueOf(count * price));
            book.markPaid(deal.id());
            book.complete(deal.id(), null);
        }
        MarketBook restored = MarketBook.read(book.write());
        long now = System.currentTimeMillis();
        assertEquals(new java.math.BigDecimal("3"), restored.recentItemUnitPrice("minecraft:diamond", "note", now));
        assertNull(restored.recentItemUnitPrice("minecraft:diamond", "copper", now));
        assertNull(restored.recentItemUnitPrice("minecraft:diamond", "note", now + 86_400_001));
    }

    @Test
    void shopReferenceIsOptional() {
        assertNull(ShopPrices.quote("minecraft:diamond", "note"));
        ShopPriceApi.install((itemId, currency) -> "minecraft:diamond".equals(itemId) && "note".equals(currency)
                ? BigInteger.valueOf(12) : null);
        assertEquals(BigInteger.valueOf(12), ShopPrices.quote("minecraft:diamond", "note"));
        assertNull(ShopPrices.quote("minecraft:stick", "note"));
    }

    @Test
    void configKeepsUnknownKeysAndFallsBackWhenTheCurrencyIdIsWrong() throws Exception {
        Path path = Files.createTempFile("marketmod", ".json");
        Files.writeString(path, """
                {"currency":"NOT VALID","pageSize":3,"future":true}
                """);
        MarketConfig read = MarketConfig.read(path);
        assertEquals("note", read.currency());
        assertEquals(3, read.pageSize());
        assertTrue(Files.readString(path).contains("future"));
    }

    @Test
    void commandTreeRegistersTheEnglishRoot() throws Exception {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        Method register = MarketCommands.class.getDeclaredMethod("register", CommandDispatcher.class,
                Class.forName("net.minecraft.command.CommandRegistryAccess"),
                Class.forName("net.minecraft.server.command.CommandManager$RegistrationEnvironment"));
        register.setAccessible(true);
        register.invoke(null, dispatcher, null, null);
        assertTrue(dispatcher.getRoot().getChild("market") != null);
        assertTrue(dispatcher.getRoot().getChild("market").getChild("land") != null);
        assertTrue(dispatcher.getRoot().getChild("market").getChild("list") != null);
        assertTrue(dispatcher.getRoot().getChild("market").getChild("ui") != null);
        assertTrue(dispatcher.getRoot().getChild("market").getChild("offer").getChild("currency") != null);
    }

    @Test
    void commandsStayAsciiAndPlayerSentencesStayChinese() throws Exception {
        for (String literal : MarketCommandNames.LITERALS) {
            assertTrue(literal.matches("[a-z]+"), literal);
        }
        for (int i = 0; i < MarketCommandNames.USAGE.length(); i++) {
            assertTrue(MarketCommandNames.USAGE.charAt(i) < 128);
        }
        String hook = MarketDesk.sentence("bank_hook_missing");
        String grant = MarketDesk.sentence("land_grant_missing");
        String shop = MarketDesk.sentence("shop_missing");
        assertTrue(hook.contains("储蓄"));
        assertTrue(hook.contains("卖方"));
        assertTrue(grant.contains("土地出让账户"));
        assertTrue(shop.contains("自定义价格"));
        assertFalse(hook.contains("P2P"));
        assertFalse(grant.contains("API"));
        String mod = Files.readString(Path.of("src/main/resources/fabric.mod.json"));
        assertTrue(mod.contains("\"id\": \"marketmod\""));
        assertTrue(mod.contains("\"environment\": \"*\""));
    }

    @Test
    void selectedCurrencySurvivesSaveAndIsUsedForDebitAndRefund() {
        Listing goods = Listing.item(UUID.randomUUID(), ALICE, "Alice", Proceeds.SELLER, PriceMode.CUSTOM,
                BigInteger.valueOf(3), "copper", 2, "minecraft:diamond", item(ALICE, 2, 3).item());
        Listing loaded = Listing.read(goods.write());
        assertEquals("copper", loaded.currency());
        MarketBook book = new MarketBook(); book.add(loaded);
        FakePayments pay = new FakePayments();
        Checkout.Outcome result = Checkout.buy(book, loaded.id(), BOB, "Bob", loaded.customTotal(), pay,
                (listing, buyer, name) -> Handoff.Outcome.failure("gate"), () -> {});
        assertEquals("gate", result.code());
        assertEquals("copper", pay.paidCurrency);
        assertEquals("copper", pay.refundedCurrency);
        assertEquals(ListingStatus.OPEN, book.get(loaded.id()).orElseThrow().status());
    }

    @Test
    void quoteMaximumRejectsHigherOrInvalidAmount() {
        assertTrue(MarketDesk.withinMaximum(BigInteger.TEN, BigInteger.TEN));
        assertTrue(MarketDesk.withinMaximum(BigInteger.ONE, BigInteger.TEN));
        assertFalse(MarketDesk.withinMaximum(BigInteger.valueOf(11), BigInteger.TEN));
        assertFalse(MarketDesk.withinMaximum(BigInteger.ONE, BigInteger.ZERO));
        assertFalse(MarketDesk.withinMaximum(BigInteger.ZERO, BigInteger.TEN));
        assertFalse(MarketDesk.withinMaximum(null, BigInteger.TEN));
    }

    @Test
    void currencyIdsCannotInjectCommandsOrUseUnsupportedPersonalSyntax() {
        assertTrue(dev.jdguo.market.integrate.MarketCurrencies.validId("note"));
        assertTrue(dev.jdguo.market.integrate.MarketCurrencies.validId("custom_2"));
        assertFalse(dev.jdguo.market.integrate.MarketCurrencies.validId("note buy"));
        assertFalse(dev.jdguo.market.integrate.MarketCurrencies.validId("personal:123"));
        assertFalse(dev.jdguo.market.integrate.MarketCurrencies.validId(null));
    }

    private static Listing item(UUID seller, int count, int price) {
        return item(UUID.randomUUID(), seller, count, price);
    }

    private static Listing item(UUID id, UUID seller, int count, int price) {
        NbtCompound item = new NbtCompound();
        item.putString("id", "minecraft:diamond");
        item.putInt("Count", count);
        return Listing.item(id, seller, "Alice", Proceeds.SELLER, PriceMode.CUSTOM,
                BigInteger.valueOf(price), "note", count, "minecraft:diamond", item);
    }

    private static final class FakePayments implements Payments {
        private java.util.Set<String> recorded = java.util.Set.of();
        private boolean receiptUnavailable;

        @Override
        public SettlementResult receipt(String reference) {
            if (receiptUnavailable) return SettlementResult.fail("receipt_unavailable", "");
            return recorded.contains(reference) ? SettlementResult.ok() : SettlementResult.fail("unrecorded", "");
        }

        private boolean failSeller;
        private boolean failRefund;
        private int sellerPays;
        private int grantPays;
        private int refundSeller;
        private int refundGrant;
        private String lastReference = "";
        private String paidCurrency;
        private String refundedCurrency;

        @Override
        public SettlementResult paySeller(UUID buyer, UUID seller, String currency, BigInteger amount, String reference) {
            sellerPays++;
            paidCurrency = currency;
            lastReference = reference;
            return failSeller ? SettlementResult.fail("insufficient_funds", "") : SettlementResult.ok();
        }

        @Override
        public SettlementResult payLandGrant(UUID buyer, String currency, BigInteger amount, String reference) {
            grantPays++;
            lastReference = reference;
            return SettlementResult.ok();
        }

        @Override
        public SettlementResult refundSeller(UUID seller, UUID buyer, String currency, BigInteger amount, String reference) {
            refundSeller++;
            refundedCurrency = currency;
            return failRefund ? SettlementResult.fail("bank_failed", "") : SettlementResult.ok();
        }

        @Override
        public SettlementResult refundLandGrant(UUID buyer, String currency, BigInteger amount, String reference) {
            refundGrant++;
            return SettlementResult.ok();
        }
    }
}
