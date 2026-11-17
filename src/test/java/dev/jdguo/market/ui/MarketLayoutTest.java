package dev.jdguo.market.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MarketLayoutTest {
 @Test void scaledViewportsStayInsideBounds(){
  for(int[] size:new int[][]{{320,240},{427,240},{640,360},{800,440},{854,480},{1280,720},{1920,1080}}){var l=MarketLayout.at(size[0],size[1]);
   for(var b:new MarketLayout.Box[]{l.listings(),l.details(),l.order(),l.book()}){assertTrue(b.x()>=0&&b.y()>=62);assertTrue(b.width()>0&&b.height()>0);assertTrue(b.x()+b.width()<=size[0]);assertTrue(b.y()+b.height()<=size[1]-36);}
   assertTrue(l.order().height()>=138);
   if(!l.compact()){assertTrue(l.details().y()+l.details().height()<l.order().y());assertTrue(l.details().x()+l.details().width()<l.book().x());assertTrue(l.listings().x()+l.listings().width()<l.order().x());}
  }
 }
 @Test void compactUsesTabs(){assertTrue(MarketLayout.at(799,600).compact());assertTrue(MarketLayout.at(900,360).compact());assertFalse(MarketLayout.at(800,440).compact());}
}
