package dev.jdguo.market.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PaymentDisplayTest {
 @Test void zeroResidualHasNoNoteLabel(){assertEquals("",PaymentDisplay.extra("0.0000","央行券"));}
 @Test void realExtraTenderRemainsVisible(){assertEquals("；另付 0.25 自定义券",PaymentDisplay.extra("0.25","自定义券"));}
 @Test void malformedIsNotPositive(){assertFalse(PaymentDisplay.positive(""));assertFalse(PaymentDisplay.positive("wrong"));}
}
