package dev.jdguo.market.api;

import com.google.gson.JsonObject;
import dev.jdguo.market.integrate.LandBridge;
import dev.jdguo.market.listing.*;
import dev.jdguo.market.world.InitialLandSettings;
import dev.jdguo.market.world.MarketSavedData;
import net.minecraft.server.MinecraftServer;
import java.math.BigInteger;
import java.math.BigDecimal;
import dev.jdguo.market.integrate.BankBridge;
import java.util.*;

/** All first-sale entry points share this quote; listings never become implicit executed orders. */
public final class LandPriceQuote {
    private LandPriceQuote() {}
    public static JsonObject estimate(MinecraftServer server, List<ChunkRef> chunks) {
        var settings = InitialLandSettings.get(server);
        BigInteger total = BigInteger.ZERO;
        int[] owners = settings.dynamicEnabled ? LandBridge.nearbyOwnerCounts(server, chunks, settings.radius) : new int[chunks.size()];
        var book = MarketSavedData.get(server).book();
        var listings = settings.dynamicEnabled ? book.all().stream().filter(l -> l.kind() != ListingKind.ITEM
                && !l.initialGrant() && l.proceeds() == Proceeds.SELLER && l.status() == ListingStatus.OPEN)
                .filter(l -> { var land = LandBridge.parcel(server, l.parcelId()); return land != null && land.owner().equals(l.seller()) && land.chunks().containsAll(l.chunks()); }).toList() : List.<Listing>of();
        var signals = settings.dynamicEnabled ? book.landSaleSignals() : List.<LandSaleSignals.Signal>of();
        long now = Math.floorDiv(System.currentTimeMillis(), 60000L) * 60000L; // stable minute buckets; structural changes still reprice immediately
        List<NearbyLandSignals.Point> offeredPoints = new ArrayList<>(), soldPoints = new ArrayList<>();
        for (var signal : signals) soldPoints.add(new NearbyLandSignals.Point(signal.chunk(), signal.seller(),
                LandPriceMath.weight(signal.time(), now, settings.decayHours * 3600000L)));
        var soldIndex = new NearbyLandSignals(soldPoints, settings.radius);
        var selectedIndex = new NearbyLandSignals(chunks.stream().map(c -> new NearbyLandSignals.Point(c, new UUID(0, 0), 10000)).toList(), settings.radius);
        int highListings = 0, unavailableListings = 0;
        for (Listing listing : listings) {
            // Only nearby offers can affect this request; far-away offers need no valuation.
            if (listing.chunks().stream().noneMatch(selectedIndex::any)) continue;
            BigInteger reference;
            try { reference = withoutListingPressure(server, listing.chunks(), settings, soldIndex); }
            catch (RuntimeException unavailable) { unavailableListings++; continue; }
            BigDecimal askValue, referenceValue;
            if (settings.currency.equals(listing.currency())) {
                askValue = new BigDecimal(listing.customTotal()); referenceValue = new BigDecimal(reference);
            } else {
                askValue = LandListingEligibility.centralValue(BankBridge.landQuote(listing.currency(), listing.customTotal(), "note"), listing.currency(), listing.customTotal());
                referenceValue = LandListingEligibility.centralValue(BankBridge.landQuote(settings.currency, reference, "note"), settings.currency, reference);
            }
            if (askValue == null || referenceValue == null) { unavailableListings++; continue; }
            if (!LandListingEligibility.within(askValue, referenceValue, settings.listingMaxPercent)) { highListings++; continue; }
            for (ChunkRef c : listing.chunks()) offeredPoints.add(new NearbyLandSignals.Point(c, listing.seller(), 10000));
        }
        var offeredIndex = new NearbyLandSignals(offeredPoints, settings.radius);
        int maxOwners = 0, maxListings = 0; long maxSales = 0;
        for (int i = 0; i < chunks.size(); i++) {
            ChunkRef chunk = chunks.get(i);
            int listedSellers = Math.toIntExact(offeredIndex.weight(chunk) / 10000);
            long weight = soldIndex.weight(chunk);
            total = total.add(LandPriceMath.price(settings.price, owners[i], settings.ownerBps, listedSellers, settings.listingBps, weight, settings.saleBps));
            maxOwners = Math.max(maxOwners, owners[i]); maxListings = Math.max(maxListings, listedSellers); maxSales = Math.max(maxSales, weight);
        }
        JsonObject out = new JsonObject(); out.addProperty("total", total.toString());
        out.addProperty("currency", settings.currency); out.addProperty("dynamic", settings.dynamicEnabled);
        out.addProperty("basePrice", settings.price.toString()); out.addProperty("nearbyOwnersMax", maxOwners);
        out.addProperty("listedSellersMax", maxListings); out.addProperty("saleWeightBpsMax", maxSales);
        out.addProperty("listingMaxPercent", settings.listingMaxPercent);
        out.addProperty("excludedHighListings", highListings); out.addProperty("excludedUnpricedListings", unavailableListings);
        out.addProperty("pricingDetail", settings.dynamicEnabled ? "逐区块计价：基准 × (1 + 业主人数×" + settings.ownerBps + "基点) ÷ (1 + 挂牌人数×" + settings.listingBps + "基点 + 成交权重×" + settings.saleBps + "基点)；100基点=1%；仅计不超过无挂牌压力参考价" + settings.listingMaxPercent + "%的挂牌，高价排除" + highListings + "笔，无有效换算排除" + unavailableListings + "笔"
                : "按基准价逐区块累计；动态地价关闭");
        return out;
    }
    /** Sum exactly the same offered area, using demand and completed sales only. No recursive estimate(). */
    private static BigInteger withoutListingPressure(MinecraftServer server, List<ChunkRef> chunks, InitialLandSettings settings, NearbyLandSignals soldIndex) {
        if (chunks.isEmpty()) throw new IllegalArgumentException("empty listing");
        BigInteger total = BigInteger.ZERO;
        for (int start = 0; start < chunks.size(); start += 1024) {
            var batch = chunks.subList(start, Math.min(chunks.size(), start + 1024));
            int[] owners = LandBridge.nearbyOwnerCounts(server, batch, settings.radius);
            for (int i = 0; i < batch.size(); i++) total = total.add(LandPriceMath.price(settings.price, owners[i], settings.ownerBps,
                    0, 0, soldIndex.weight(batch.get(i)), settings.saleBps));
        }
        return total;
    }

}
