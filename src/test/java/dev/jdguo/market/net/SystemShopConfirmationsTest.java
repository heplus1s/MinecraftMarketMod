package dev.jdguo.market.net;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SystemShopConfirmationsTest {
    @Test void boundOneUseAndExpiring() {
        var confirmations=new SystemShopConfirmations();var a=UUID.randomUUID();var b=UUID.randomUUID();
        var order=confirmations.put(a,"minecraft:iron_sword",3,false,new BigDecimal("0.5123"),10);
        assertNull(confirmations.consume(b,order.token(),11));
        var actual=confirmations.consume(a,order.token(),12);assertNotNull(actual);
        assertFalse(actual.buying());assertEquals(3,actual.count());assertEquals("minecraft:iron_sword",actual.item());
        assertEquals(new BigDecimal("0.5123"),actual.total());assertNull(confirmations.consume(a,order.token(),13));
        order=confirmations.put(a,"minecraft:bow",1,true,BigDecimal.ONE,20);
        assertNull(confirmations.consume(a,order.token(),30021));
    }
    @Test void replacementAndDisconnectInvalidatePreviousQuote() {
        var c=new SystemShopConfirmations();var id=UUID.randomUUID();
        var old=c.put(id,"minecraft:bow",1,true,BigDecimal.ONE,0);
        c.put(id,"minecraft:bow",2,true,BigDecimal.TEN,1);
        assertNull(c.consume(id,old.token(),2));
        var next=c.put(id,"minecraft:bow",1,true,BigDecimal.ONE,3);c.remove(id);
        assertNull(c.consume(id,next.token(),4));
        assertThrows(IllegalArgumentException.class,()->c.put(id,"minecraft:bow",-1,true,BigDecimal.ONE,0));
    }
}
