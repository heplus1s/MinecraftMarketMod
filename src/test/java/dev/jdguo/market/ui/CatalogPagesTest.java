package dev.jdguo.market.ui;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CatalogPagesTest {
 private static com.google.gson.JsonObject packet(long id,int page,int pages,String currency){return JsonParser.parseString("{\"requestId\":"+id+",\"page\":"+page+",\"pages\":"+pages+",\"currency\":\""+currency+"\",\"items\":[{\"itemId\":\"item"+page+"\"}]}").getAsJsonObject();}
 @Test void waitsForAllPagesAndPreservesOrder(){var p=new CatalogPages();assertNull(p.accept(packet(1,1,2,"note")));var s=p.accept(packet(1,0,2,"note"));assertNotNull(s);assertEquals(2,s.items().size());assertEquals("item0",s.items().get(0).get("itemId").getAsString());}
 @Test void newerGenerationDiscardsOldOrMixedCurrency(){var p=new CatalogPages();assertNull(p.accept(packet(1,0,2,"note")));assertNull(p.accept(packet(2,1,2,"gold")));assertNull(p.accept(packet(1,1,2,"note")));assertNull(p.accept(packet(2,0,2,"note")));assertEquals("gold",p.accept(packet(2,0,2,"gold")).currency());}
 @Test void rejectsMalformedAndInvalidRanges(){var p=new CatalogPages();assertNull(p.accept(packet(1,2,2,"note")));assertNull(p.accept(packet(1,0,0,"note")));assertNull(p.accept(new com.google.gson.JsonObject()));assertNotNull(p.accept(packet(1,0,1,"note")));}
}
