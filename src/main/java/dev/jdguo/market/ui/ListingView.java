package dev.jdguo.market.ui;

import java.util.List;

/**
 * One listing as a later chest menu or client UI should read it.
 * New getters may be added. Do not persist this snapshot; the world save is
 * the listing book.
 */
public record ListingView(
        String id,
        String shortId,
        String kind,
        String sellerName,
        String sellerId,
        boolean landGrant,
        boolean shopPrice,
        String unitPrice,
        String totalPrice,
        String currency,
        int count,
        String itemId,
        String dimension,
        String parcelId,
        String parcelName,
        List<String> chunks
) {
    public ListingView {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        sellerName = sellerName == null ? "" : sellerName;
        unitPrice = unitPrice == null ? "" : unitPrice;
        totalPrice = totalPrice == null ? "" : totalPrice;
        currency = currency == null ? "" : currency;
        itemId = itemId == null ? "" : itemId;
        dimension = dimension == null ? "" : dimension;
        parcelId = parcelId == null ? "" : parcelId;
        parcelName = parcelName == null ? "" : parcelName;
    }
}
