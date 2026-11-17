package dev.jdguo.market.net;
import org.junit.jupiter.api.Test;
import java.math.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PersonalConfirmationsTest {
    UUID player=UUID.randomUUID(),coin=UUID.randomUUID();
    PersonalConfirmations book=new PersonalConfirmations();
    PersonalConfirmations.Order offer(){return book.put(player,coin,BigInteger.TEN,new BigDecimal("1.25"),1000);}
    @Test void exactOrderAndSingleUse(){var o=offer();var result=book.consume(player,o.token(),1001);assertEquals(coin,result.coin());assertEquals(BigInteger.TEN,result.quantity());assertEquals(new BigDecimal("1.25"),result.maximum());assertNull(book.consume(player,o.token(),1002));}
    @Test void expiry(){var o=offer();assertNull(book.consume(player,o.token(),31000));}
    @Test void playerIsolation(){var o=offer();assertNull(book.consume(UUID.randomUUID(),o.token(),1001));assertNotNull(book.consume(player,o.token(),1002));}
    @Test void newestQuoteReplacesPrevious(){var old=offer();var next=offer();assertNotEquals(old.token(),next.token());assertNull(book.consume(player,old.token(),1001));}
    @Test void disconnectClears(){var o=offer();book.remove(player);assertNull(book.consume(player,o.token(),1001));}
    @Test void invalidAmountsNeverIssued(){assertThrows(IllegalArgumentException.class,()->book.put(player,coin,BigInteger.ZERO,BigDecimal.ONE,1));assertThrows(IllegalArgumentException.class,()->book.put(player,coin,BigInteger.ONE,BigDecimal.ZERO,1));}
    @Test void paymentQuoteBoundToToken(){
        String json="{\"physical\":\"3\",\"change\":\"0.5\"}";
        var o=book.put(player,coin,BigInteger.TEN,new BigDecimal("1.25"),"diamond",json,false,1000);
        var found=book.consume(player,o.token(),1001);
        assertEquals("diamond",found.tender());assertEquals(json,found.quote());assertFalse(found.legacy());
        assertNull(book.consume(player,o.token(),1002));
    }
    @Test void otherTenderInvalidatesPreviousQuote(){
        var old=offer();var next=book.put(player,coin,BigInteger.TEN,BigDecimal.ONE,"copper","{}",false,1001);
        assertNotEquals(old.token(),next.token());assertEquals("copper",book.consume(player,next.token(),1002).tender());
        assertNull(book.consume(player,old.token(),1003));
    }
    @Test void personalPaymentPreservesExactQuote(){
        String tender="personal:"+UUID.randomUUID();String quote="{\"paymentAmount\":\"0.12345678\",\"rate\":\"0.012345678\"}";
        var o=book.put(player,coin,BigInteger.TEN,BigDecimal.ONE,tender,quote,false,1000);
        var read=book.consume(player,o.token(),1001);assertEquals(tender,read.tender());assertEquals(quote,read.quote());
    }
}
