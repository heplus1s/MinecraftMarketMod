package dev.jdguo.market.listing;
import com.google.gson.JsonObject;
import java.math.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LandListingEligibilityTest {
    @Test void inclusive120PercentBoundary() {
        assertTrue(LandListingEligibility.within(new BigDecimal("120"),new BigDecimal("100"),120));
        assertFalse(LandListingEligibility.within(new BigDecimal("120.0001"),new BigDecimal("100"),120));
    }
    @Test void wholeAreaTotalsNotPerChunkAgainstTotal() {
        assertTrue(LandListingEligibility.within(new BigDecimal("1200"),new BigDecimal("1000"),120));
        assertFalse(LandListingEligibility.within(new BigDecimal("1200.1"),new BigDecimal("1000"),120));
        assertFalse(LandListingEligibility.within(new BigDecimal("1200"),new BigDecimal("100"),120));
    }
    @Test void thresholdConfigurableAndBigNumbersExact() {
        assertFalse(LandListingEligibility.within(new BigDecimal("120"),new BigDecimal("100"),110));
        BigDecimal b=new BigDecimal(BigInteger.TEN.pow(100));assertTrue(LandListingEligibility.within(b.multiply(new BigDecimal("1.2")),b,120));
    }
    private JsonObject quote(){var q=new JsonObject();q.addProperty("success",true);q.addProperty("denomination","copper");q.addProperty("paymentCurrency","note");q.addProperty("total","100");q.addProperty("centralValue","12.5");return q;}
    @Test void acceptsMatchingCurrentBankValueOnly() {
        assertEquals(new BigDecimal("12.5"),LandListingEligibility.centralValue(quote(),"copper",BigInteger.valueOf(100)));
        assertNull(LandListingEligibility.centralValue(quote(),"diamond",BigInteger.valueOf(100)));
        assertNull(LandListingEligibility.centralValue(quote(),"copper",BigInteger.TEN));
        var q=quote();q.addProperty("success",false);assertNull(LandListingEligibility.centralValue(q,"copper",BigInteger.valueOf(100)));
    }
    @Test void missingMalformedAndUnpricedIgnored() {
        assertNull(LandListingEligibility.centralValue(new JsonObject(),"copper",BigInteger.valueOf(100)));
        for(String raw:new String[]{"NaN","-1","0","1e999999"}){var q=quote();q.addProperty("centralValue",raw);assertNull(LandListingEligibility.centralValue(q,"copper",BigInteger.valueOf(100)));}
    }
}
