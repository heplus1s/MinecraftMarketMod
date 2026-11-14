package dev.jdguo.market.listing;

import java.math.BigInteger;
import java.util.UUID;

public final class Deal {
    public enum Stage {
        RESERVED,
        PAID;

        public String token() {
            return this == PAID ? "paid" : "reserved";
        }

        public static Stage parse(String token) {
            return "paid".equals(token) ? PAID : "reserved".equals(token) ? RESERVED : null;
        }
    }

    private final UUID id;
    private final UUID listingId;
    private final UUID buyer;
    private final String buyerName;
    private final UUID payee;
    private final BigInteger amount;
    private final String currency;
    private final Proceeds proceeds;
    private final String reference;
    private final String refundReference;
    private Stage stage;

    public Deal(UUID id, UUID listingId, UUID buyer, String buyerName, UUID payee, BigInteger amount,
                String currency, Proceeds proceeds, String reference, String refundReference, Stage stage) {
        this.id = id;
        this.listingId = listingId;
        this.buyer = buyer;
        this.buyerName = buyerName == null ? "" : buyerName;
        this.payee = payee;
        this.amount = amount;
        this.currency = currency == null ? "" : currency;
        this.proceeds = proceeds;
        this.reference = reference;
        this.refundReference = refundReference;
        this.stage = stage;
    }

    public UUID id() {
        return id;
    }

    public UUID listingId() {
        return listingId;
    }

    public UUID buyer() {
        return buyer;
    }

    public String buyerName() {
        return buyerName;
    }

    public UUID payee() {
        return payee;
    }

    public BigInteger amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public Proceeds proceeds() {
        return proceeds;
    }

    public String reference() {
        return reference;
    }

    public String refundReference() {
        return refundReference;
    }

    public Stage stage() {
        return stage;
    }

    void markPaid() {
        stage = Stage.PAID;
    }
}
