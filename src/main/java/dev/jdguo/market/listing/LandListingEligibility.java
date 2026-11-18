package dev.jdguo.market.listing;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.BigInteger;

/** Compare whole-listing totals for the identical area; never compare unlike currency numbers. */
public final class LandListingEligibility {
    private LandListingEligibility() {}
    public static boolean within(BigDecimal listingValue, BigDecimal referenceValue, int percent) {
        if (percent < 1 || percent > 10000 || listingValue == null || referenceValue == null
                || listingValue.signum() <= 0 || referenceValue.signum() <= 0) return false;
        return listingValue.multiply(BigDecimal.valueOf(100)).compareTo(referenceValue.multiply(BigDecimal.valueOf(percent))) <= 0;
    }
    /** Only accept a successful, matching bank quote in central notes; no guessed/last-known rate. */
    public static BigDecimal centralValue(JsonObject quote, String currency, BigInteger total) {
        try {
            if (quote == null || !quote.get("success").getAsBoolean()
                    || !currency.equals(quote.get("denomination").getAsString())
                    || !"note".equals(quote.get("paymentCurrency").getAsString())
                    || !total.equals(new BigInteger(quote.get("total").getAsString()))) return null;
            String raw = quote.get("centralValue").getAsString();
            if (!raw.matches("[0-9]{1,128}(\\.[0-9]{1,16})?")) return null;
            BigDecimal value = new BigDecimal(raw);
            return value.signum() > 0 ? value : null;
        } catch (RuntimeException invalid) { return null; }
    }
}
