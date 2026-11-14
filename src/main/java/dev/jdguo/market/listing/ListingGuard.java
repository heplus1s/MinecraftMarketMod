package dev.jdguo.market.listing;

import java.util.List;
import java.util.UUID;

/**
 * Locks a parcel while a market listing is open. Gifts and the land mod's unpaid
 * sale use a different reference, so they stop. The purchase reference
 * {@code sale:} plus this listing id is allowed through.
 */
public final class ListingGuard {
    private final MarketBook book;

    public ListingGuard(MarketBook book) {
        this.book = book;
    }

    public boolean allowsParcelTransfer(UUID parcelId, String reference) {
        Listing listing = book.listingForParcel(parcelId);
        if (listing == null) {
            return true;
        }
        return listing.kind() == ListingKind.PARCEL && SaleReference.matches(listing.id(), reference);
    }

    public boolean allowsChunkTransfer(UUID parcelId, ChunkRef chunk, String reference) {
        Listing listing = book.listingForParcel(parcelId);
        if (listing == null) {
            return true;
        }
        if (!SaleReference.matches(listing.id(), reference)) {
            return false;
        }
        if (listing.kind() == ListingKind.PARCEL) {
            return true;
        }
        return listing.covers(chunk);
    }

    public boolean allowsSplit(UUID parcelId, List<ChunkRef> moving) {
        Listing listing = book.listingForParcel(parcelId);
        if (listing == null) {
            return true;
        }
        String permit = LandPermit.current();
        if (permit == null || !SaleReference.matches(listing.id(), permit)) {
            return false;
        }
        return listing.kind() == ListingKind.CHUNKS && listing.sameChunks(moving);
    }

    public boolean allowsAbandon(UUID parcelId) {
        return book.listingForParcel(parcelId) == null;
    }

    public boolean allowsMerge(UUID target, UUID source) {
        return book.listingForParcel(target) == null && book.listingForParcel(source) == null;
    }

    public boolean allowsJoin(UUID parcelId) {
        return book.listingForParcel(parcelId) == null;
    }
}
