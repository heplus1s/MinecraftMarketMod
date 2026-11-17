package dev.jdguo.market.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MarketAccessPolicyTest {
    @Test void forgedAdminHintCannotCancelAnotherPlayersOrder() {
        assertFalse(MarketAccessPolicy.canCancel(false, true, false));
        assertFalse(MarketAccessPolicy.canCancel(false, false, false));
    }
    @Test void revocationIsEffectiveOnNextOperation() {
        assertTrue(MarketAccessPolicy.canCancel(false, true, true));
        assertFalse(MarketAccessPolicy.canCancel(false, true, false));
    }
    @Test void ownerKeepsOrdinaryCancellationRights() {
        for (boolean requested : new boolean[]{false,true})
            for (boolean operator : new boolean[]{false,true})
                assertTrue(MarketAccessPolicy.canCancel(true,requested,operator));
        assertFalse(MarketAccessPolicy.canCancel(false,false,true));
    }
}
