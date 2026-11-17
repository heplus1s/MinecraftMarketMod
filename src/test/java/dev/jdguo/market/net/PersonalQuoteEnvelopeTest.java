package dev.jdguo.market.net;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersonalQuoteEnvelopeTest {
    @Test void failedCrossQuotePreservesCodeWithoutTender(){
        for(String code:new String[]{"funds","paused","insufficient_treasury"}){
            var quote=JsonParser.parseString("{\"ok\":false,\"code\":\""+code+"\"}").getAsJsonObject();
            PersonalQuoteEnvelope.bindTender(quote,"personal:requested");
            assertFalse(quote.get("ok").getAsBoolean());assertEquals(code,quote.get("code").getAsString());
            assertEquals("personal:requested",quote.get("tender").getAsString());
        }
    }
    @Test void successMustStillMatchExactTender(){
        var missing=JsonParser.parseString("{\"ok\":true}").getAsJsonObject();
        assertThrows(IllegalStateException.class,()->PersonalQuoteEnvelope.bindTender(missing,"diamond"));
        var wrong=JsonParser.parseString("{\"ok\":true,\"tender\":\"note\"}").getAsJsonObject();
        assertThrows(IllegalStateException.class,()->PersonalQuoteEnvelope.bindTender(wrong,"diamond"));
        assertDoesNotThrow(()->PersonalQuoteEnvelope.bindTender(wrong,"note"));
    }
}
