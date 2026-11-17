package dev.jdguo.market.listing;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One saved offer. Unknown keys live in {@code extra} and are written back.
 */
public final class Listing {
    private static final Set<String> KEYS = Set.of(
            "Id", "Kind", "Seller", "SellerName", "Proceeds", "PriceMode", "UnitPrice", "Currency",
            "Count", "ItemId", "Item", "Parcel", "ParcelName", "Dimension", "Chunks", "Created",
            "Status", "ReservedFor");

    private static final Comparator<ChunkRef> ORDER = Comparator
            .comparing(ChunkRef::dimension)
            .thenComparingInt(ChunkRef::x)
            .thenComparingInt(ChunkRef::z);

    private final UUID id;
    private final ListingKind kind;
    private final UUID seller;
    private final String sellerName;
    private final Proceeds proceeds;
    private final PriceMode priceMode;
    private final BigInteger unitPrice;
    private final String currency;
    private int count;
    private final String itemId;
    private final NbtCompound item;
    private final UUID parcelId;
    private final String parcelName;
    private final String dimension;
    private final List<ChunkRef> chunks;
    private final long createdAt;
    private final NbtCompound extra;
    private ListingStatus status;
    private UUID reservedFor;

    private Listing(UUID id, ListingKind kind, UUID seller, String sellerName, Proceeds proceeds, PriceMode priceMode,
                    BigInteger unitPrice, String currency, int count, String itemId, NbtCompound item, UUID parcelId,
                    String parcelName, String dimension, List<ChunkRef> chunks, long createdAt, ListingStatus status,
                    UUID reservedFor, NbtCompound extra) {
        this.id = id;
        this.kind = kind;
        this.seller = seller;
        this.sellerName = sellerName == null ? "" : sellerName;
        this.proceeds = proceeds;
        this.priceMode = priceMode;
        this.unitPrice = unitPrice;
        this.currency = currency;
        this.count = count;
        this.itemId = itemId == null ? "" : itemId;
        this.item = item == null ? new NbtCompound() : item.copy();
        this.parcelId = parcelId;
        this.parcelName = parcelName == null ? "" : parcelName;
        this.dimension = dimension == null ? "" : dimension;
        this.chunks = List.copyOf(chunks);
        this.createdAt = createdAt;
        this.status = status;
        this.reservedFor = reservedFor;
        this.extra = extra == null ? new NbtCompound() : extra.copy();
    }

    public static Listing item(UUID id, UUID seller, String sellerName, Proceeds proceeds, PriceMode priceMode,
                               BigInteger unitPrice, String currency, int count, String itemId, NbtCompound item) {
        if (priceMode == PriceMode.CUSTOM && (unitPrice == null || unitPrice.signum() <= 0)) {
            throw new IllegalArgumentException("price");
        }
        if (priceMode == PriceMode.SHOP) {
            unitPrice = BigInteger.ZERO;
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count");
        }
        return new Listing(id, ListingKind.ITEM, seller, sellerName, proceeds, priceMode, unitPrice, currency, count,
                itemId, item, null, "", "", List.of(), System.currentTimeMillis(), ListingStatus.OPEN, null, new NbtCompound());
    }

    public static Listing land(UUID id, ListingKind kind, UUID seller, String sellerName, Proceeds proceeds,
                               BigInteger price, String currency, UUID parcelId, String parcelName, String dimension,
                               List<ChunkRef> chunks) {
        if (kind != ListingKind.PARCEL && kind != ListingKind.CHUNKS) {
            throw new IllegalArgumentException("kind");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price");
        }
        List<ChunkRef> ordered = new ArrayList<>(chunks);
        ordered.sort(ORDER);
        return new Listing(id, kind, seller, sellerName, proceeds, PriceMode.CUSTOM, price, currency, ordered.size(),
                "", new NbtCompound(), parcelId, parcelName, dimension, ordered, System.currentTimeMillis(),
                ListingStatus.OPEN, null, new NbtCompound());
    }

    public static Listing initialGrant(UUID id, BigInteger total, String currency, String dimension, List<ChunkRef> chunks) {
        Listing listing = land(id, ListingKind.CHUNKS, new UUID(0, 0), "服务器土地出让", Proceeds.LAND_GRANT,
                total, currency, id, "未占用土地首售", dimension, chunks);
        listing.extra.putBoolean("InitialGrant", true);
        return listing;
    }

    public boolean initialGrant() { return extra.getBoolean("InitialGrant"); }

    public static Listing read(NbtCompound tag) {
        if (tag == null || !tag.containsUuid("Id") || !tag.containsUuid("Seller")) {
            return null;
        }
        ListingKind kind = ListingKind.parse(tag.getString("Kind"));
        Proceeds proceeds = Proceeds.parse(tag.getString("Proceeds"));
        PriceMode priceMode = PriceMode.parse(tag.getString("PriceMode"));
        ListingStatus status = ListingStatus.parse(tag.getString("Status"));
        if (kind == null || proceeds == null || priceMode == null || status == null) {
            return null;
        }
        BigInteger unitPrice = money(tag.getString("UnitPrice"));
        String currency = tag.getString("Currency");
        if (currency == null || currency.isBlank()) {
            return null;
        }
        UUID reservedFor = tag.containsUuid("ReservedFor") ? tag.getUuid("ReservedFor") : null;
        NbtCompound extra = unknown(tag);
        if (kind == ListingKind.ITEM) {
            if (priceMode == PriceMode.SHOP) {
                unitPrice = BigInteger.ZERO;
            } else if (unitPrice == null || unitPrice.signum() <= 0) {
                return null;
            }
            int count = tag.getInt("Count");
            if (count <= 0 || !tag.contains("Item", NbtElement.COMPOUND_TYPE) || tag.getString("ItemId").isBlank()) {
                return null;
            }
            return new Listing(tag.getUuid("Id"), kind, tag.getUuid("Seller"), tag.getString("SellerName"), proceeds,
                    priceMode, unitPrice, currency, count, tag.getString("ItemId"), tag.getCompound("Item"), null, "",
                    "", List.of(), tag.getLong("Created"), status, reservedFor, extra);
        }
        if (priceMode != PriceMode.CUSTOM || unitPrice == null || unitPrice.signum() <= 0 || !tag.containsUuid("Parcel")) {
            return null;
        }
        List<ChunkRef> chunks = readChunks(tag.getList("Chunks", NbtElement.COMPOUND_TYPE));
        if (chunks.isEmpty()) {
            return null;
        }
        return new Listing(tag.getUuid("Id"), kind, tag.getUuid("Seller"), tag.getString("SellerName"), proceeds,
                priceMode, unitPrice, currency, chunks.size(), "", new NbtCompound(), tag.getUuid("Parcel"),
                tag.getString("ParcelName"), tag.getString("Dimension"), chunks, tag.getLong("Created"), status,
                reservedFor, extra);
    }

    public NbtCompound write() {
        NbtCompound tag = extra.copy();
        tag.putUuid("Id", id);
        tag.putString("Kind", kind.token());
        tag.putUuid("Seller", seller);
        tag.putString("SellerName", sellerName);
        tag.putString("Proceeds", proceeds.token());
        tag.putString("PriceMode", priceMode.token());
        tag.putString("UnitPrice", unitPrice.toString());
        tag.putString("Currency", currency);
        tag.putInt("Count", count);
        tag.putString("ItemId", itemId);
        if (kind == ListingKind.ITEM) {
            tag.put("Item", item.copy());
        }
        if (parcelId != null) {
            tag.putUuid("Parcel", parcelId);
        }
        tag.putString("ParcelName", parcelName);
        tag.putString("Dimension", dimension);
        NbtList chunkList = new NbtList();
        for (ChunkRef chunk : chunks) {
            NbtCompound one = new NbtCompound();
            one.putString("Dim", chunk.dimension());
            one.putInt("X", chunk.x());
            one.putInt("Z", chunk.z());
            chunkList.add(one);
        }
        tag.put("Chunks", chunkList);
        tag.putLong("Created", createdAt);
        tag.putString("Status", status.token());
        if (reservedFor != null) {
            tag.putUuid("ReservedFor", reservedFor);
        }
        return tag;
    }

    public BigInteger customTotal() {
        if (priceMode != PriceMode.CUSTOM) {
            throw new IllegalStateException("shop price is read at purchase");
        }
        return unitPrice.multiply(BigInteger.valueOf(pricedUnits()));
    }

    public int pricedUnits() {
        return kind == ListingKind.ITEM ? count : 1;
    }

    public boolean sameChunks(List<ChunkRef> other) {
        return other != null && new HashSet<>(chunks).equals(new HashSet<>(other));
    }

    public boolean covers(ChunkRef chunk) {
        return chunks.contains(chunk);
    }

    public String shortId() {
        return id.toString().substring(0, 8);
    }

    public UUID id() {
        return id;
    }

    public ListingKind kind() {
        return kind;
    }

    public UUID seller() {
        return seller;
    }

    public String sellerName() {
        return sellerName;
    }

    public Proceeds proceeds() {
        return proceeds;
    }

    public PriceMode priceMode() {
        return priceMode;
    }

    public BigInteger unitPrice() {
        return unitPrice;
    }

    public String currency() {
        return currency;
    }

    public int count() {
        return count;
    }

    public String itemId() {
        return itemId;
    }

    public NbtCompound item() {
        NbtCompound result = item.copy();
        result.putInt("MarketCount", count);
        return result;
    }

    /** Split escrow only, never create or copy inventory ownership. Called on server thread. */
    Listing split(int quantity) {
        if (kind != ListingKind.ITEM || status != ListingStatus.OPEN || quantity <= 0 || quantity >= count)
            throw new IllegalArgumentException("quantity");
        Listing part = new Listing(UUID.randomUUID(), kind, seller, sellerName, proceeds, priceMode,
                unitPrice, currency, quantity, itemId, item, null, "", "", List.of(), createdAt,
                ListingStatus.OPEN, null, extra);
        count -= quantity;
        return part;
    }
    void restoreSplit(Listing part) {
        if (status != ListingStatus.OPEN || part.status != ListingStatus.OPEN || !seller.equals(part.seller)
                || !itemId.equals(part.itemId) || count + part.count > 2304) throw new IllegalStateException("split");
        count += part.count;
    }
    public int shopPercent() { return extra.contains("ShopPercent") ? extra.getInt("ShopPercent") : 100; }
    public void shopPercent(int percent) {
        if (priceMode != PriceMode.SHOP || percent < 1 || percent > 1000) throw new IllegalArgumentException("percent");
        extra.putInt("ShopPercent", percent);
    }

    public UUID parcelId() {
        return parcelId;
    }

    public String parcelName() {
        return parcelName;
    }

    public String dimension() {
        return dimension;
    }

    public List<ChunkRef> chunks() {
        return chunks;
    }

    public long createdAt() {
        return createdAt;
    }

    public ListingStatus status() {
        return status;
    }

    public UUID reservedFor() {
        return reservedFor;
    }

    public boolean hasExtra(String key) {
        return extra.contains(key);
    }

    public String extraString(String key) {
        return extra.getString(key);
    }

    void reserve(UUID buyer) {
        status = ListingStatus.RESERVED;
        reservedFor = buyer;
    }

    void release() {
        status = ListingStatus.OPEN;
        reservedFor = null;
    }

    private static BigInteger money(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigInteger(raw);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static List<ChunkRef> readChunks(NbtList list) {
        List<ChunkRef> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound chunk = list.getCompound(i);
            if (!chunk.contains("Dim", NbtElement.STRING_TYPE)) {
                continue;
            }
            chunks.add(new ChunkRef(chunk.getString("Dim"), chunk.getInt("X"), chunk.getInt("Z")));
        }
        chunks.sort(ORDER);
        return chunks;
    }

    private static NbtCompound unknown(NbtCompound source) {
        NbtCompound extra = new NbtCompound();
        for (String key : source.getKeys()) {
            if (!KEYS.contains(key)) {
                NbtElement value = source.get(key);
                if (value != null) {
                    extra.put(key, value.copy());
                }
            }
        }
        return extra;
    }
}
