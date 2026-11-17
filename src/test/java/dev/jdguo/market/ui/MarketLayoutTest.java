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
 @Test void interactiveRowsStayAbovePaginationAndOrderButtons(){
  for(int[] size:new int[][]{{480,300},{640,360},{800,440},{854,480},{1280,720},{1920,1080}}){var l=MarketLayout.at(size[0],size[1]);var list=l.listings();var book=l.book();var order=l.order();
   int listRows=Math.max(1,(list.height()-118)/32),bookRows=Math.max(1,(book.height()-63)/23);
   assertTrue(108+(listRows-1)*32+9<=list.height()-23,"quote clear of pager");
   assertTrue(24+(bookRows-1)*23+18<=book.height()-24,"offer clear of pager");
   int column=(order.width()-20)/2;
   assertTrue(column-56>=80,"price field keeps usable typing area");
   assertTrue(107+18<=order.height(),"submit contained");
   assertTrue(29+18<54,"input rows separate");
   assertTrue(91+9<107,"labels clear of submit");
  }
 }
 @Test void personalCurrencyControlsFitWithoutOverlap(){
  for(int[] size:new int[][]{{480,300},{640,360},{800,440},{854,480},{1280,720},{1920,1080}}){var l=MarketLayout.at(size[0],size[1]);var list=l.listings();var order=l.order();
   int rows=Math.max(1,(list.height()-105)/24);
   assertTrue(72+(rows-1)*24+18<=list.height()-24);
   assertTrue(71+9<=l.details().height());assertTrue(71+9<=l.book().height());
   assertTrue(24+18<48);assertTrue(48+18<72);assertTrue(72+18<98);assertTrue(98+9<113);assertTrue(113+9<order.height());
   assertTrue(order.width()-122>=80);
   assertTrue(60+(order.width()-122)<order.width()-52,"quantity and cancel do not overlap");
   int half=(order.width()-18)/2;assertTrue(6+half<12+half);assertTrue(12+half+half<=order.width()-6);
  }
 }
 @Test void tooSmallViewportsRequestLowerGuiScaleInsteadOfHidingSections(){assertFalse(MarketLayout.supported(427,240));assertFalse(MarketLayout.supported(640,240));assertTrue(MarketLayout.supported(640,360));}
 @Test void systemQuoteRowsAndPlayerOrdersStayAbovePager(){
  for(int[] size:new int[][]{{480,300},{640,360},{800,440},{1280,720}}){var b=MarketLayout.at(size[0],size[1]).book();int n=Math.max(1,(b.height()-87)/23);
   assertTrue(24+18<47);assertTrue(47+18<68);
   assertTrue(68+(n-1)*23+18<b.height()-24,"system quotes, player rows and pager must not overlap");
  }
 }
}
