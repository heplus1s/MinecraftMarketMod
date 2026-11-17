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
}
