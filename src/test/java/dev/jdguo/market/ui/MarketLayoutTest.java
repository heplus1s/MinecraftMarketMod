package dev.jdguo.market.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MarketLayoutTest {
 @Test void allSupportedScaledViewportsKeepSpotPanelsSimultaneously(){
  for(int[] size:new int[][]{{480,300},{640,360},{800,440},{854,480},{1280,720},{1920,1080}}){var l=MarketLayout.at(size[0],size[1]);
   assertFalse(l.compact());
   for(var b:new MarketLayout.Box[]{l.listings(),l.details(),l.order(),l.book()}){assertTrue(b.x()>=0&&b.y()>=30);assertTrue(b.width()>=100&&b.height()>0);assertTrue(b.x()+b.width()<=size[0]);assertTrue(b.y()+b.height()<=size[1]-22);}
   assertEquals(132,l.order().height());
   assertTrue(l.details().y()+l.details().height()<l.order().y());assertTrue(l.details().x()+l.details().width()<l.book().x());assertTrue(l.listings().x()+l.listings().width()<l.order().x());
  }
 }
 @Test void tooSmallViewportsRequestLowerGuiScaleInsteadOfHidingSections(){assertFalse(MarketLayout.supported(427,240));assertFalse(MarketLayout.supported(640,240));assertTrue(MarketLayout.supported(640,360));}
}
