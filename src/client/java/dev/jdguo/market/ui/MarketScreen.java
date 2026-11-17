package dev.jdguo.market.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Spot-style navigation over server escrow, actual offers and completed trades only. */
public final class MarketScreen extends Screen {
    private JsonObject data;
    private MarketLayout.Layout layout;
    private String selected="",currency="",price="1",count="64",buyCount="1",notice="",filter="",percent="100";
    private int tab,page,bookPage;
    private boolean mine,confirming,pending,linked;
    private TextFieldWidget priceField,countField,buyField,filterField,percentField;
    private final List<JsonObject> rows=new ArrayList<>();
    public MarketScreen(JsonObject data){super(Text.literal("市场现货交易"));this.data=data;}
    static String text(JsonObject o,String key){try{return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}catch(RuntimeException e){return "";}}
    private List<JsonObject> array(String key){List<JsonObject> r=new ArrayList<>();if(data.has(key)&&data.get(key).isJsonArray())for(JsonElement v:data.getAsJsonArray(key))if(v.isJsonObject())r.add(v.getAsJsonObject());return r;}
    private String currencyLabel(String id){for(var c:array("currencies"))if(id.equals(text(c,"id")))return text(c,"label");return "note".equals(id)?"央行券":id.startsWith("personal:")?"个人币（名称待同步）":id;}
    private String name(JsonObject row){String n=text(row,"itemName");if(!n.isBlank())return n;String id=text(row,"itemId");return id.isBlank()?"领地 "+text(row,"parcelName"):id;}
    private boolean item(JsonObject row){return row!=null&&!text(row,"itemId").isBlank();}
    private boolean own(JsonObject row){return row!=null&&client.player!=null&&client.player.getUuidAsString().equals(text(row,"sellerId"));}
    void update(JsonObject next){capture();data=next;confirming=false;rebuild();}
    void result(JsonObject result){pending=false;confirming=false;notice="";if(result.has("lines"))for(var line:result.getAsJsonArray("lines"))notice+=line.getAsString()+" ";MarketUiClient.refresh();rebuild();}
    private void capture(){if(priceField!=null)price=priceField.getText();if(countField!=null)count=countField.getText();if(buyField!=null)buyCount=buyField.getText();if(filterField!=null)filter=filterField.getText();if(percentField!=null)percent=percentField.getText();}
    private void rebuild(){if(client!=null){clearChildren();init();}}
    @Override protected void init(){
        layout=MarketLayout.at(width,height);priceField=null;countField=null;buyField=null;filterField=null;percentField=null;
        if(width<320||height<240){button("关闭",Math.max(4,width-62),8,54,this::close);return;}
        if(currency.isBlank())currency=text(data,"defaultCurrency");if(currency.isBlank())currency="note";
        button("刷新",width-116,8,50,()->{capture();confirming=false;MarketUiClient.refresh();});button("关闭",width-62,8,54,this::close);
        button(mine?"我的委托":"全部商品",8,34,82,()->{capture();mine=!mine;page=0;rebuild();});
        if(layout.compact()){int w=(width-106)/4;for(int i=0;i<4;i++){int t=i;button(new String[]{"商品","行情","卖盘","交易"}[i],96+i*w,34,w-3,()->{capture();tab=t;rebuild();});}}
        rows.clear();for(var row:array("listings"))if((!mine||own(row))&&(filter.isBlank()||(name(row)+text(row,"sellerName")+currencyLabel(text(row,"currency"))).toLowerCase().contains(filter.toLowerCase())))rows.add(row);
        if(!layout.compact()||tab==0)listingPane();
        if(!layout.compact()||tab==2)bookPane();
        if(!layout.compact()||tab==3)orderPane();
    }
    private void listingPane(){var b=layout.listings();filterField=field(b.x()+6,b.y()+23,b.width()-12,filter,"搜索商品/币种/卖家",64);int capacity=Math.max(1,(b.height()-76)/33);page=Math.min(page,Math.max(0,(rows.size()-1)/capacity));
        for(int i=0;i<capacity&&page*capacity+i<rows.size();i++){var row=rows.get(page*capacity+i);button(fit(name(row)+" ×"+text(row,"count"),b.width()-20),b.x()+6,b.y()+47+i*33,b.width()-12,()->select(row));}
        button("◀",b.x()+6,b.y()+b.height()-23,30,()->{capture();page=Math.max(0,page-1);rebuild();});button("搜索",b.x()+40,b.y()+b.height()-23,48,()->{capture();page=0;rebuild();});button("▶",b.x()+b.width()-36,b.y()+b.height()-23,30,()->{capture();page++;rebuild();});}
    private void select(JsonObject row){capture();selected=text(row,"id");buyCount="1";confirming=false;bookPage=0;if(layout.compact())tab=1;rebuild();}
    private JsonObject selection(){for(var r:array("listings"))if(selected.equals(text(r,"id")))return r;return null;}
    private List<JsonObject> offers(){var chosen=selection();List<JsonObject> list=new ArrayList<>();if(chosen==null)return list;for(var r:array("listings"))if(text(chosen,"itemId").equals(text(r,"itemId"))&&text(chosen,"currency").equals(text(r,"currency"))&&item(r))list.add(r);list.sort((a,b)->{try{return new BigInteger(text(a,"unitPrice")).compareTo(new BigInteger(text(b,"unitPrice")));}catch(RuntimeException e){return 0;}});return list;}
    private void bookPane(){var b=layout.book();int capacity=Math.max(1,(b.height()-75)/23);var offers=offers();bookPage=Math.min(bookPage,Math.max(0,(offers.size()-1)/capacity));for(int i=0;i<capacity&&bookPage*capacity+i<offers.size();i++){var r=offers.get(bookPage*capacity+i);button(fit(text(r,"unitPrice")+"  ×"+text(r,"count")+"  "+text(r,"sellerName"),b.width()-22),b.x()+6,b.y()+29+i*23,b.width()-12,()->select(r));}button("◀",b.x()+6,b.y()+b.height()-24,32,()->{capture();bookPage=Math.max(0,bookPage-1);rebuild();});button("▶",b.x()+44,b.y()+b.height()-24,32,()->{capture();bookPage++;rebuild();});}
    private void orderPane(){var b=layout.order();int gap=10,w=(b.width()-22)/2,x=b.x()+6,y=b.y()+26;var row=selection();
        buyField=field(x,y+18,w,buyCount,"买入数量",4);buyField.setChangedListener(v->{buyCount=v;confirming=false;});
        var buy=button(own(row)?"撤销我的挂牌":confirming?"确认支付并买入":"买入选中卖单",x,y+94,w,()->{
            capture();if(row==null||pending)return;if(own(row)){send("cancel "+text(row,"id"));return;}
            try{int n=item(row)?Integer.parseInt(buyCount):1;if(n<=0||n>Integer.parseInt(text(row,"count")))throw new IllegalArgumentException();
                BigInteger total=item(row)?new BigInteger(text(row,"unitPrice")).multiply(BigInteger.valueOf(n)):new BigInteger(text(row,"totalPrice"));
                if(!confirming){confirming=true;notice="确认买入 "+n+" "+(item(row)?"件":"笔领地")+"，最高支付 "+total+" "+currencyLabel(text(row,"currency"))+"；银行扣款。";rebuild();return;}
                send("buy "+selected+" "+total+(item(row)?" "+n:""));
            }catch(RuntimeException ex){notice="数量须为正整数且不超过挂牌剩余量；暂无报价时不能成交。";confirming=false;}});
        buy.active=row!=null&&!pending;buyField.setEditable(item(row)&&!pending);
        int sx=x+w+gap;button(fit(currencyLabel(currency),w-8),sx,y,w,()->{capture();var cs=array("currencies");if(cs.isEmpty())return;int i=0;for(;i<cs.size();i++)if(currency.equals(text(cs.get(i),"id")))break;currency=text(cs.get((i+1)%cs.size()),"id");rebuild();});
        button(linked?"商店浮动比例 %":"固定单价",sx,y+23,(w-6)/2,()->{capture();linked=!linked;rebuild();});
        if(linked)percentField=field(sx+(w+6)/2,y+23,(w-6)/2,percent,"商店价格百分比",4);else priceField=field(sx+(w+6)/2,y+23,(w-6)/2,price,"每件单价",18);
        countField=field(sx,y+63,w,count,"挂牌数量（主库存同属性物品）",4);
        var sell=button("托管并挂牌",sx,y+94,w,()->{capture();try{int n=Integer.parseInt(count);long p=Long.parseLong(linked?percent:price);if(n<1||n>2304||p<1||(linked&&p>1000))throw new IllegalArgumentException();send(linked?"listshop "+n+" "+currency+" "+p:"list "+p+" "+n+" "+currency);}catch(RuntimeException ex){notice="数量 1–2304；固定价为正整数；浮动比例 1–1000%（100% 为商店参考价）。";}});sell.active=!pending;
    }
    private void send(String action){if(MarketUiClient.action(action)){pending=true;confirming=false;notice="已提交，请勿重复操作。";rebuild();}else notice="未连接市场服务，操作未发送。";}
    private ButtonWidget button(String label,int x,int y,int w,Runnable action){return addDrawableChild(ButtonWidget.builder(Text.literal(label),b->action.run()).dimensions(x,y,Math.max(1,w),18).build());}
    private TextFieldWidget field(int x,int y,int w,String value,String label,int max){var f=new TextFieldWidget(textRenderer,x,y,Math.max(1,w),18,Text.literal(label));f.setMaxLength(max);f.setText(value);addDrawableChild(f);return f;}
    private String fit(String value,int w){return textRenderer.trimToWidth(value,Math.max(1,w));}
    private void line(DrawContext c,String value,int x,int y,int w,int color){c.drawText(textRenderer,fit(value,w),x,y,color,false);}
    private void panel(DrawContext c,MarketLayout.Box b,String title){c.fill(b.x(),b.y(),b.x()+b.width(),b.y()+b.height(),0xff152130);line(c,title,b.x()+6,b.y()+7,b.width()-12,0xffdde8f2);}
    private void chart(DrawContext c,MarketLayout.Box b,JsonObject row){List<Double> prices=new ArrayList<>();if(row!=null)for(var t:array("trades"))if(text(row,"itemId").equals(text(t,"itemId"))&&text(row,"currency").equals(text(t,"currency")))try{double p=Double.parseDouble(text(t,"amount"))/Integer.parseInt(text(t,"count"));if(Double.isFinite(p))prices.add(p);}catch(RuntimeException ignored){}
        int x=b.x()+8,y=b.y()+94,w=b.width()-16,h=b.height()-116;if(h<24)return;c.fill(x,y,x+w,y+h,0xff0b1420);
        if(prices.size()<2){line(c,"成交后显示真实价格走势",x+6,y+8,w-12,0xff99abbc);return;}
        double min=prices.stream().mapToDouble(Double::doubleValue).min().orElse(0),max=prices.stream().mapToDouble(Double::doubleValue).max().orElse(1);double span=Math.max(1,max-min);int px=0,py=0;
        for(int i=0;i<prices.size();i++){int xx=x+i*(w-1)/(prices.size()-1),yy=y+h-1-(int)((prices.get(i)-min)/span*(h-10));if(i>0){int steps=Math.max(1,Math.abs(xx-px));for(int k=0;k<=steps;k++){int dx=px+(xx-px)*k/steps,dy=py+(yy-py)*k/steps;c.fill(dx,dy,dx+1,dy+2,0xff6ce0bb);}}px=xx;py=yy;}
        line(c,"最近 "+prices.size()+" 笔成交（非等时间间隔）",x+3,y+h+3,w-6,0xff99abbc);
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){renderBackground(c);c.fill(0,0,width,height,0xff0b1420);if(width<320||height<240){line(c,"请降低 GUI 缩放，至少需要 320×240。",8,42,width-16,0xffffc66d);super.render(c,mx,my,delta);return;}
        line(c,"市场 / 现货交易",8,12,width-132,0xff6ce0bb);
        if(!layout.compact()||tab==0){var b=layout.listings();panel(c,b,mine?"我的委托":"商品 / 挂牌列表");int capacity=Math.max(1,(b.height()-76)/33);for(int i=0;i<capacity&&page*capacity+i<rows.size();i++){var r=rows.get(page*capacity+i);line(c,text(r,"unitPrice")+" "+currencyLabel(text(r,"currency")),b.x()+8,b.y()+67+i*33,b.width()-16,0xff99abbc);}if(rows.isEmpty())line(c,"暂无挂牌，请先托管商品",b.x()+8,b.y()+48,b.width()-16,0xff99abbc);}
        var row=selection();if(!layout.compact()||tab==1){var b=layout.details();panel(c,b,row==null?"行情 / 选择商品":name(row)+" / "+currencyLabel(text(row,"currency")));if(row!=null){line(c,"选中卖价 "+text(row,"unitPrice")+" · 剩余 "+text(row,"count"),b.x()+8,b.y()+27,b.width()-16,0xff6ce0bb);line(c,"卖家 "+text(row,"sellerName"),b.x()+8,b.y()+44,b.width()-16,0xffb5c5d5);line(c,"商店收购 "+empty(text(row,"shopBid"))+" / 出售 "+empty(text(row,"shopAsk")),b.x()+8,b.y()+61,b.width()-16,0xffb5c5d5);line(c,Boolean.parseBoolean(text(row,"shopPrice"))?"随商店浮动 "+text(row,"shopPercent")+"% · 单价向上取整":"固定单价 · 可按数量买入",b.x()+8,b.y()+78,b.width()-16,0xff99abbc);}else line(c,"从商品列表选择一笔挂牌",b.x()+8,b.y()+29,b.width()-16,0xff99abbc);chart(c,b,row);}
        if(!layout.compact()||tab==2){var b=layout.book();panel(c,b,"卖盘 · 单价 / 剩余数量");line(c,"仅显示真实卖单，不虚构买盘",b.x()+84,b.y()+b.height()-20,b.width()-90,0xff99abbc);}
        if(!layout.compact()||tab==3){var b=layout.order();panel(c,b,"买入选中卖单                         卖出主库存物品");int w=(b.width()-22)/2,x=b.x()+6,y=b.y()+26;line(c,item(row)?"买入数量（件）":"领地按整笔购买",x,y+5,w,0xff99abbc);String total="—";try{if(row!=null)total=item(row)?new BigInteger(text(row,"unitPrice")).multiply(new BigInteger(buyCount)).toString():text(row,"totalPrice");}catch(RuntimeException ignored){}line(c,"合计 "+total+" "+(row==null?"":currencyLabel(text(row,"currency"))),x,y+44,w,0xff6ce0bb);line(c,"银行付款 · 余额不足不成交",x,y+62,w,0xff99abbc);line(c,"数量（同物品、同属性）",x+w+10,y+52,w,0xff99abbc);}
        line(c,notice.isBlank()?"实物托管 · 按件部分成交 · 背包满则进入待领记录":notice,8,height-25,width-16,0xffffc66d);super.render(c,mx,my,delta);
        if(!notice.isBlank()&&my>=height-34)c.drawOrderedTooltip(textRenderer,textRenderer.wrapLines(Text.literal(notice),Math.max(120,width-48)),mx,my);
    }
    private static String empty(String s){return s.isBlank()?"暂无":s;}
    @Override public void resize(net.minecraft.client.MinecraftClient client,int width,int height){capture();super.resize(client,width,height);}
    @Override public boolean shouldPause(){return false;}
    @Override public void removed(){capture();}
}
