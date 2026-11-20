package dev.jdguo.market.listing;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Listings, unfinished deals, and items waiting for a free inventory slot.
 * Call it on the server thread. Unknown save keys are written back.
 * Completed-trade history is capped. Listings, escrowed items, and unfinished deals are not.
 */
public final class MarketBook {
    public static final int VERSION = 1;
    public static final int TRADE_LIMIT = 2000;

    private static final long ITEM_SIGNAL_WINDOW_MILLIS = Duration.ofHours(24).toMillis();
    private static final int ITEM_SIGNAL_MIN_UNITS = 16;
    private static final int ITEM_SIGNAL_MIN_PAIRS = 3;
    private static final int DEMAND_PAIR_CAP = 64;
    private static final int DEMAND_PARTICIPANT_CAP = 128;

    private static final Set<String> ROOT_KEYS = Set.of("version", "listings", "trades", "pending", "deals", "pendingDeliveryLocks", "landSaleSignals");

    private final Map<UUID, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, Deal> deals = new LinkedHashMap<>();
    private final Map<UUID, UUID> parcelIndex = new LinkedHashMap<>();
    private final List<NbtCompound> trades = new ArrayList<>();
    private final List<PendingItem> pending = new ArrayList<>();
    private final List<NbtCompound> preservedListings = new ArrayList<>();
    private final List<NbtCompound> preservedDeals = new ArrayList<>();
    private NbtCompound extra = new NbtCompound();
    private NbtCompound pendingDeliveryLocks = new NbtCompound();
    private final LandSaleSignals landSaleSignals = new LandSaleSignals();
    public List<LandSaleSignals.Signal> landSaleSignals() { return landSaleSignals.all(); }
    private void recordLandSale(Listing listing, long now) {
        if (listing == null || listing.kind() == ListingKind.ITEM || listing.initialGrant() || listing.proceeds() != Proceeds.SELLER) return;
        landSaleSignals.record(listing.chunks(), listing.seller(), now);
    }

    public int size() {
        return listings.size();
    }

    public int countOf(UUID seller) {
        int count = 0;
        for (Listing listing : listings.values()) {
            if (listing.seller().equals(seller)) {
                count++;
            }
        }
        return count;
    }

    public Optional<Listing> get(UUID id) {
        return Optional.ofNullable(listings.get(id));
    }

    public Listing listingForParcel(UUID parcelId) {
        UUID id = parcelIndex.get(parcelId);
        return id == null ? null : listings.get(id);
    }

    public List<Listing> all() {
        return List.copyOf(listings.values());
    }

    /** Initial grants stay private until delivery, refund or recovery; all extant journals block reuse. */
    public boolean hasPendingInitialGrant(Set<ChunkRef> selected) {
        if (selected.isEmpty()) return false;
        for (Listing listing : listings.values()) {
            if (listing.initialGrant() && listing.chunks().stream().anyMatch(selected::contains)) return true;
        }
        return false;
    }

    public List<Deal> deals() {
        return List.copyOf(deals.values());
    }

    public boolean add(Listing listing) {
        if (listing.kind() != ListingKind.ITEM) {
            if (listing.parcelId() == null || parcelIndex.containsKey(listing.parcelId())) {
                return false;
            }
        }
        listings.put(listing.id(), listing);
        index(listing);
        return true;
    }

    public Lookup find(String raw) {
        String token = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace("-", "");
        if (!token.matches("[0-9a-f]{4,32}")) {
            return Lookup.NONE;
        }
        List<Listing> found = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.id().toString().replace("-", "").startsWith(token)) {
                found.add(listing);
            }
        }
        if (found.size() == 1) {
            return Lookup.one(found.get(0));
        }
        if (found.isEmpty()) {
            return Lookup.NONE;
        }
        return Lookup.AMBIGUOUS;
    }

    public List<Listing> search(UUID seller, String sellerName, String itemId, boolean landOnly) {
        List<Listing> found = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.initialGrant() || listing.status() != ListingStatus.OPEN) {
                continue;
            }
            if (seller != null && !listing.seller().equals(seller)) {
                continue;
            }
            if (sellerName != null && !listing.sellerName().equalsIgnoreCase(sellerName)) {
                continue;
            }
            if (itemId != null && !itemMatches(listing, itemId)) {
                continue;
            }
            if (landOnly && listing.kind() == ListingKind.ITEM) {
                continue;
            }
            found.add(listing);
        }
        found.sort(Comparator.comparingLong(Listing::createdAt).reversed().thenComparing(listing -> listing.id().toString()));
        return found;
    }

    public Deal reserve(UUID listingId, UUID buyer, String buyerName, BigInteger amount) {
        return reserve(listingId, buyer, buyerName, amount, null);
    }

    Deal reserve(UUID listingId, UUID buyer, String buyerName, BigInteger amount, UUID governmentOperator) {
        Listing listing = listings.get(listingId);
        if (listing == null || listing.status() != ListingStatus.OPEN) {
            throw new IllegalStateException("not open");
        }
        if (governmentOperator != null && (listing.kind() == ListingKind.ITEM || listing.initialGrant()
                || listing.proceeds() != Proceeds.SELLER || !"note".equals(listing.currency()))) {
            throw new IllegalArgumentException("government_buyback");
        }
        UUID dealId = UUID.randomUUID();
        listing.reserve(buyer);
        Deal deal = new Deal(dealId, listing.id(), buyer, buyerName, listing.seller(), amount, listing.currency(),
                governmentOperator == null ? listing.proceeds() : Proceeds.GOVERNMENT_BUYBACK,
                SaleReference.payment(dealId), SaleReference.refund(dealId), Deal.Stage.RESERVED, governmentOperator);
        deals.put(dealId, deal);
        return deal;
    }

    public Listing splitItem(UUID id, int quantity) {
        Listing original = listings.get(id);
        if (original == null) throw new IllegalArgumentException("missing");
        Listing part = original.split(quantity);
        listings.put(part.id(), part);
        return part;
    }
    public void restoreSplit(UUID originalId, UUID childId) {
        Listing original = listings.get(originalId), child = listings.get(childId);
        if (original == null || child == null || deals.values().stream().anyMatch(d -> d.listingId().equals(childId))) throw new IllegalStateException("split");
        original.restoreSplit(child); listings.remove(childId);
    }
    public List<NbtCompound> recentTrades() { return trades.stream().skip(Math.max(0, trades.size()-80)).map(NbtCompound::copy).toList(); }

    public void markPaid(UUID dealId) {
        Deal deal = deals.get(dealId);
        if (deal == null) {
            throw new IllegalStateException("missing deal");
        }
        deal.markPaid();
    }

    public void abort(UUID dealId) {
        Deal deal = deals.remove(dealId);
        if (deal == null) {
            return;
        }
        Listing listing = listings.get(deal.listingId());
        if (listing != null) {
            listing.release();
        }
    }

    public void discard(UUID dealId) {
        Deal deal = deals.remove(dealId);
        if (deal == null) {
            return;
        }
        remove(deal.listingId());
    }

    public void complete(UUID dealId, NbtCompound pendingItem) {
        Deal deal = deals.remove(dealId);
        if (deal == null) {
            throw new IllegalStateException("missing deal");
        }
        Listing listing = listings.get(deal.listingId());
        remove(deal.listingId());
        if (pendingItem != null && !pendingItem.isEmpty()) {
            pending.add(new PendingItem(deal.buyer(), pendingItem.copy(), System.currentTimeMillis()));
        }
        recordLandSale(listing, System.currentTimeMillis());
        trades.add(trade(deal, listing));
        trimTrades();
    }

    public boolean remove(UUID listingId) {
        Listing listing = listings.remove(listingId);
        if (listing == null) {
            return false;
        }
        if (listing.parcelId() != null) {
            parcelIndex.remove(listing.parcelId(), listing.id());
        }
        return true;
    }

    public void addPending(UUID player, NbtCompound item) {
        if (item != null && !item.isEmpty()) {
            pending.add(new PendingItem(player, item.copy(), System.currentTimeMillis()));
        }
    }

    public List<PendingItem> pendingOf(UUID player) {
        List<PendingItem> owned = new ArrayList<>();
        for (PendingItem item : pending) {
            if (item.player().equals(player)) {
                owned.add(item);
            }
        }
        return owned;
    }

    public void replacePending(UUID player, List<PendingItem> remaining) {
        pending.removeIf(item -> item.player().equals(player));
        pending.addAll(remaining);
    }

    /** Reserved without a deal: inventory escrow operation must be checked manually after interruption. */
    public void lockItem(UUID id) {
        Listing listing = listings.get(id);
        if (listing == null || listing.kind() != ListingKind.ITEM || listing.status() != ListingStatus.OPEN)
            throw new IllegalStateException("item not open");
        listing.reserve(listing.seller());
    }
    public void unlockItem(UUID id) {
        Listing listing = listings.get(id);
        if (listing == null || listing.kind() != ListingKind.ITEM || deals.values().stream().anyMatch(d -> d.listingId().equals(id)))
            throw new IllegalStateException("not an inventory escrow");
        listing.release();
    }
    public List<String> pendingDeliveryLockOwners() { return pendingDeliveryLocks.getKeys().stream().sorted().toList(); }
    public boolean pendingDeliveryLocked(UUID player) { return pendingDeliveryLocks.contains(player.toString()); }
    public void lockPendingDelivery(UUID player) {
        if (pendingDeliveryLocked(player)) throw new IllegalStateException("pending delivery requires reconciliation");
        pendingDeliveryLocks.putBoolean(player.toString(), true);
    }
    public void unlockPendingDelivery(UUID player) { pendingDeliveryLocks.remove(player.toString()); }

    public NbtCompound write() {
        NbtCompound nbt = extra.copy();
        NbtList signals = new NbtList();
        for (var signal : landSaleSignals.all()) {
            NbtCompound row = new NbtCompound(); row.putString("Dimension", signal.chunk().dimension());
            row.putInt("X", signal.chunk().x()); row.putInt("Z", signal.chunk().z()); row.putUuid("Seller", signal.seller()); row.putLong("Time", signal.time()); signals.add(row);
        }
        nbt.put("landSaleSignals", signals);
        nbt.putInt("version", VERSION);
        nbt.put("pendingDeliveryLocks", pendingDeliveryLocks.copy());
        NbtList listingList = new NbtList();
        List<Listing> ordered = new ArrayList<>(listings.values());
        ordered.sort(Comparator.comparing(listing -> listing.id().toString()));
        for (Listing listing : ordered) {
            listingList.add(listing.write());
        }
        preservedListings.forEach(tag -> listingList.add(tag.copy()));
        nbt.put("listings", listingList);
        NbtList tradeList = new NbtList();
        trades.forEach(tag -> tradeList.add(tag.copy()));
        nbt.put("trades", tradeList);
        NbtList pendingList = new NbtList();
        for (PendingItem item : pending) {
            pendingList.add(item.write());
        }
        nbt.put("pending", pendingList);
        NbtList dealList = new NbtList();
        List<Deal> orderedDeals = new ArrayList<>(deals.values());
        orderedDeals.sort(Comparator.comparing(deal -> deal.id().toString()));
        for (Deal deal : orderedDeals) {
            dealList.add(writeDeal(deal));
        }
        preservedDeals.forEach(tag -> dealList.add(tag.copy()));
        nbt.put("deals", dealList);
        return nbt;
    }

    public static MarketBook read(NbtCompound nbt) {
        MarketBook book = new MarketBook();
        if (nbt == null) {
            return book;
        }
        book.extra = unknown(nbt, ROOT_KEYS);
        book.pendingDeliveryLocks = nbt.getCompound("pendingDeliveryLocks").copy();
        NbtList signals = nbt.getList("landSaleSignals", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < signals.size(); i++) {
            NbtCompound row = signals.getCompound(i);
            if (!row.containsUuid("Seller") || row.getString("Dimension").isBlank()) continue;
            long time = row.getLong("Time"), now = System.currentTimeMillis();
            ChunkRef chunk = new ChunkRef(row.getString("Dimension"), row.getInt("X"), row.getInt("Z"));
            book.landSaleSignals.restore(chunk, row.getUuid("Seller"), time, now);
        }
        NbtList listingList = nbt.getList("listings", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < listingList.size(); i++) {
            NbtCompound tag = listingList.getCompound(i);
            Listing listing = Listing.read(tag);
            if (listing == null || book.listings.containsKey(listing.id())) {
                book.preservedListings.add(tag.copy());
                continue;
            }
            if (listing.kind() != ListingKind.ITEM && listing.parcelId() != null && book.parcelIndex.containsKey(listing.parcelId())) {
                book.preservedListings.add(tag.copy());
                continue;
            }
            book.listings.put(listing.id(), listing);
            book.index(listing);
        }
        NbtList tradeList = nbt.getList("trades", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < tradeList.size(); i++) {
            book.trades.add(tradeList.getCompound(i).copy());
        }
        NbtList pendingList = nbt.getList("pending", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < pendingList.size(); i++) {
            PendingItem item = PendingItem.read(pendingList.getCompound(i));
            if (item != null) {
                book.pending.add(item);
            }
        }
        NbtList dealList = nbt.getList("deals", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < dealList.size(); i++) {
            NbtCompound tag = dealList.getCompound(i);
            Deal deal = readDeal(tag);
            if (deal == null || book.deals.containsKey(deal.id())) {
                book.preservedDeals.add(tag.copy());
                continue;
            }
            book.deals.put(deal.id(), deal);
        }
        return book;
    }

    public boolean hasExtra(String key) {
        return extra.contains(key);
    }

    public String extraString(String key) {
        return extra.getString(key);
    }

    public int preservedListings() {
        return preservedListings.size();
    }

    public int tradeCount() {
        return trades.size();
    }

    private void index(Listing listing) {
        if (listing.kind() != ListingKind.ITEM && listing.parcelId() != null) {
            parcelIndex.put(listing.parcelId(), listing.id());
        }
    }

    public BigDecimal recentItemUnitPrice(String itemId, String currency, long now) {
        List<ItemFill> fills = recentIndependentItemFills(itemId, currency, now);
        Set<Set<UUID>> counterparties = new HashSet<>();
        long volume = 0;
        for (ItemFill fill : fills) {
            counterparties.add(fill.pair());
            volume += fill.quantity();
        }
        if (volume < ITEM_SIGNAL_MIN_UNITS || counterparties.size() < ITEM_SIGNAL_MIN_PAIRS) {
            return null;
        }
        List<PriceSample> samples = new ArrayList<>(fills.size());
        for (ItemFill fill : fills) {
            samples.add(new PriceSample(fill.total().divide(BigDecimal.valueOf(fill.quantity()), MathContext.DECIMAL128),
                    fill.quantity()));
        }
        samples.sort(Comparator.comparing(PriceSample::unit));
        long accumulated = 0;
        for (PriceSample sample : samples) {
            accumulated += sample.quantity();
            if (accumulated >= (volume + 1) / 2) {
                return sample.unit();
            }
        }
        return null;
    }

    /** Capped independent player fills. Not a pressure or direction indicator. */
    public long recentDemandUnits(String itemId, String currency, long now) {
        List<ItemFill> fills = recentIndependentItemFills(itemId, currency, now);
        Map<Set<UUID>, Integer> unitsByPair = new HashMap<>();
        Map<UUID, Integer> unitsByParticipant = new HashMap<>();
        Set<UUID> buyers = new HashSet<>();
        Set<UUID> sellers = new HashSet<>();
        long volume = 0;
        // Prefer the latest completed fills when a pair or participant hits its cap.
        for (int i = fills.size() - 1; i >= 0; i--) {
            ItemFill fill = fills.get(i);
            Set<UUID> pair = fill.pair();
            int pairUsed = unitsByPair.getOrDefault(pair, 0);
            int buyerUsed = unitsByParticipant.getOrDefault(fill.buyer(), 0);
            int sellerUsed = unitsByParticipant.getOrDefault(fill.seller(), 0);
            int units = Math.min(fill.quantity(), Math.min(DEMAND_PAIR_CAP - pairUsed,
                    Math.min(DEMAND_PARTICIPANT_CAP - buyerUsed, DEMAND_PARTICIPANT_CAP - sellerUsed)));
            if (units <= 0) continue;
            unitsByPair.put(pair, pairUsed + units);
            unitsByParticipant.put(fill.buyer(), buyerUsed + units);
            unitsByParticipant.put(fill.seller(), sellerUsed + units);
            buyers.add(fill.buyer());
            sellers.add(fill.seller());
            volume += units;
        }
        return volume >= ITEM_SIGNAL_MIN_UNITS && unitsByPair.size() >= ITEM_SIGNAL_MIN_PAIRS
                && buyers.size() >= ITEM_SIGNAL_MIN_PAIRS && sellers.size() >= ITEM_SIGNAL_MIN_PAIRS ? volume : 0;
    }

    private List<ItemFill> recentIndependentItemFills(String itemId, String currency, long now) {
        long cutoff = now - ITEM_SIGNAL_WINDOW_MILLIS;
        List<ItemFill> samples = new ArrayList<>();
        for (NbtCompound trade : trades) {
            int count = trade.getInt("Count");
            if (!trade.getBoolean("IndependentPrice") || count <= 0
                    || !ListingKind.ITEM.token().equals(trade.getString("Kind"))
                    || !Proceeds.SELLER.token().equals(trade.getString("Proceeds"))
                    || !itemId.equals(trade.getString("ItemId")) || !currency.equals(trade.getString("Currency"))
                    || trade.getLong("At") < cutoff || trade.getLong("At") > now
                    || !trade.containsUuid("Buyer") || !trade.containsUuid("Seller")) {
                continue;
            }
            UUID buyer = trade.getUuid("Buyer");
            UUID seller = trade.getUuid("Seller");
            if (buyer.equals(seller)) {
                continue;
            }
            BigDecimal total;
            try {
                total = new BigDecimal(trade.getString("Amount"));
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (total.signum() <= 0) {
                continue;
            }
            samples.add(new ItemFill(buyer, seller, total, count));
        }
        return samples;
    }

    private record ItemFill(UUID buyer, UUID seller, BigDecimal total, int quantity) {
        Set<UUID> pair() { return Set.of(buyer, seller); }
    }

    private record PriceSample(BigDecimal unit, int quantity) {}

    private void trimTrades() {
        while (trades.size() > TRADE_LIMIT) {
            trades.remove(0);
        }
    }

    private static boolean itemMatches(Listing listing, String itemId) {
        if (listing.kind() != ListingKind.ITEM) {
            return false;
        }
        String wanted = itemId.toLowerCase(Locale.ROOT);
        String full = listing.itemId().toLowerCase(Locale.ROOT);
        if (full.equals(wanted)) {
            return true;
        }
        int colon = full.indexOf(':');
        String path = colon >= 0 ? full.substring(colon + 1) : full;
        return path.equals(wanted);
    }

    private static NbtCompound trade(Deal deal, Listing listing) {
        NbtCompound tag = new NbtCompound();
        if (deal.operator() != null) tag.putUuid("Operator", deal.operator());
        tag.putUuid("Id", UUID.randomUUID());
        tag.putUuid("Deal", deal.id());
        tag.putUuid("Listing", deal.listingId());
        tag.putUuid("Buyer", deal.buyer());
        tag.putString("BuyerName", deal.buyerName());
        if (deal.payee() != null) {
            tag.putUuid("Seller", deal.payee());
        }
        tag.putString("Amount", deal.amount().toString());
        tag.putString("Currency", deal.currency());
        tag.putString("Proceeds", deal.proceeds().token());
        tag.putString("Kind", listing == null ? "" : listing.kind().token());
        tag.putString("ItemId", listing == null ? "" : listing.itemId());
        tag.putInt("Count", listing == null ? 0 : listing.count());
        tag.putString("VariantKey", listing == null ? "unknown" : ItemVariants.key(listing.item()));
        tag.putLong("At", System.currentTimeMillis());
        tag.putBoolean("IndependentPrice", listing != null && listing.kind() == ListingKind.ITEM && deal.proceeds() == Proceeds.SELLER
                && listing.priceMode() == PriceMode.CUSTOM && listing.item().getCompound("tag").isEmpty());
        return tag;
    }

    private static NbtCompound writeDeal(Deal deal) {
        NbtCompound tag = new NbtCompound();
        if (deal.operator() != null) tag.putUuid("Operator", deal.operator());
        tag.putUuid("Id", deal.id());
        tag.putUuid("Listing", deal.listingId());
        tag.putUuid("Buyer", deal.buyer());
        tag.putString("BuyerName", deal.buyerName());
        if (deal.payee() != null) {
            tag.putUuid("Payee", deal.payee());
        }
        tag.putString("Amount", deal.amount().toString());
        tag.putString("Currency", deal.currency());
        tag.putString("Proceeds", deal.proceeds().token());
        tag.putString("Reference", deal.reference());
        tag.putString("Refund", deal.refundReference());
        tag.putString("Stage", deal.stage().token());
        return tag;
    }

    private static Deal readDeal(NbtCompound tag) {
        if (!tag.containsUuid("Id") || !tag.containsUuid("Listing") || !tag.containsUuid("Buyer")) {
            return null;
        }
        Proceeds proceeds = Proceeds.parse(tag.getString("Proceeds"));
        Deal.Stage stage = Deal.Stage.parse(tag.getString("Stage"));
        BigInteger amount = null;
        try {
            amount = new BigInteger(tag.getString("Amount"));
        } catch (NumberFormatException exception) {
            return null;
        }
        if (proceeds == null || stage == null || amount.signum() <= 0) {
            return null;
        }
        UUID payee = tag.containsUuid("Payee") ? tag.getUuid("Payee") : null;
        return new Deal(tag.getUuid("Id"), tag.getUuid("Listing"), tag.getUuid("Buyer"), tag.getString("BuyerName"),
                payee, amount, tag.getString("Currency"), proceeds, tag.getString("Reference"), tag.getString("Refund"), stage,
                tag.containsUuid("Operator") ? tag.getUuid("Operator") : null);
    }

    private static NbtCompound unknown(NbtCompound source, Set<String> known) {
        NbtCompound extra = new NbtCompound();
        for (String key : source.getKeys()) {
            if (!known.contains(key)) {
                NbtElement value = source.get(key);
                if (value != null) {
                    extra.put(key, value.copy());
                }
            }
        }
        return extra;
    }

    public static final class Lookup {
        public static final Lookup NONE = new Lookup(null, false);
        public static final Lookup AMBIGUOUS = new Lookup(null, true);

        private final Listing listing;
        private final boolean ambiguous;

        private Lookup(Listing listing, boolean ambiguous) {
            this.listing = listing;
            this.ambiguous = ambiguous;
        }

        public static Lookup one(Listing listing) {
            return new Lookup(listing, false);
        }

        public Listing listing() {
            return listing;
        }

        public boolean ambiguous() {
            return ambiguous;
        }
    }

    public record PendingItem(UUID player, NbtCompound item, long createdAt) {
        public PendingItem {
            item = item == null ? new NbtCompound() : item.copy();
        }

        private NbtCompound write() {
            NbtCompound tag = new NbtCompound();
            tag.putUuid("Player", player);
            tag.put("Item", item.copy());
            tag.putLong("Created", createdAt);
            return tag;
        }

        private static PendingItem read(NbtCompound tag) {
            if (!tag.containsUuid("Player") || !tag.contains("Item", NbtElement.COMPOUND_TYPE)) {
                return null;
            }
            return new PendingItem(tag.getUuid("Player"), tag.getCompound("Item"), tag.getLong("Created"));
        }
    }
}
