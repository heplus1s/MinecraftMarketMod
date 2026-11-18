package dev.jdguo.market.api;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LandQuoteMatchTest {
    private JsonObject quote() { return JsonParser.parseString("{\"buyable\":true,\"kind\":\"listing\",\"listingId\":\"a\",\"revision\":\"1\",\"total\":\"10\",\"currency\":\"note\",\"scope\":[1,2]}").getAsJsonObject(); }
    @Test void concurrentInitialDemandOrSupplyChangeRequiresNewConfirmation() {
        var shown = quote(); shown.addProperty("kind", "initial"); shown.addProperty("listingId", "");
        shown.addProperty("total", "100");
        for (String next : new String[]{"110", "99"}) {
            var fresh = shown.deepCopy(); fresh.addProperty("total", next);
            assertFalse(LandQuoteMatch.matches(shown, fresh));
        }
        var changedPolicy = shown.deepCopy(); changedPolicy.addProperty("revision", "2");
        assertFalse(LandQuoteMatch.matches(shown, changedPolicy));
        assertTrue(LandQuoteMatch.matches(shown, shown.deepCopy()));
    }
    @Test void identicalQuoteAccepted() { assertTrue(LandQuoteMatch.matches(quote(), quote())); }
    @Test void changedPriceCurrencyRevisionOrListingRejected() {
        for (String key : new String[]{"total","currency","revision","listingId","kind"}) {
            var fresh = quote(); fresh.addProperty(key,"changed"); assertFalse(LandQuoteMatch.matches(quote(), fresh),key);
        }
    }
    @Test void changedScopeOrUnavailableRejected() {
        var fresh = quote(); fresh.add("scope", JsonParser.parseString("[2,3]")); assertFalse(LandQuoteMatch.matches(quote(),fresh));
        fresh=quote(); fresh.addProperty("buyable",false); assertFalse(LandQuoteMatch.matches(quote(),fresh));
        assertFalse(LandQuoteMatch.matches(quote(), new JsonObject()));
    }
}
