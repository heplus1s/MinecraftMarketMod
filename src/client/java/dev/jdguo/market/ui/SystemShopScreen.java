package dev.jdguo.market.ui;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** One clear quantity -> actual server quote -> confirm flow, never a fabricated stock order. */
final class SystemShopScreen extends Screen {
    private final Screen parent;
    private final String item, name;
    private final boolean buying;
    private TextFieldWidget quantity;
    private ButtonWidget confirm;
    private String amount="1", token="", total="", currency="央行券", message="输入整数件数后预览。总价包含逐件价格浮动。";
    private int quotedCount;
    SystemShopScreen(Screen parent,String item,String name,boolean buying) { super(Text.literal(buying?"从系统商店买入":"卖给系统商店"));this.parent=parent;this.item=item;this.name=name;this.buying=buying; }
    void accept(JsonObject out) {
        if(out.has("itemId") && (!item.equals(out.get("itemId").getAsString()) || buying!=out.get("buying").getAsBoolean()))return;
        if(out.has("count") && !out.has("completed") && !amount.equals(out.get("count").getAsString()))return;
        token=out.has("token")?out.get("token").getAsString():"";
        total=out.has("total")?out.get("total").getAsString():"";
        if(out.has("currencyName"))currency=out.get("currencyName").getAsString();
        if(out.has("count"))quotedCount=out.get("count").getAsInt();
        message=out.has("message")?out.get("message").getAsString():"";
        if(!token.isEmpty())message="核对总额后确认。报价 30 秒有效，变化后须重新预览。";
        if(confirm!=null)confirm.active=!token.isEmpty();
    }
    @Override protected void init() {
        token="";total="";
        if(width<300||height<240){addDrawableChild(ButtonWidget.builder(Text.literal("返回市场"),b->close()).dimensions(8,8,100,20).build());return;}
        message="输入整数件数后预览。总价包含逐件价格浮动。";
        int w=Math.min(340,width-24),x=(width-w)/2,y=Math.max(12,(height-220)/2);
        quantity=new TextFieldWidget(textRenderer,x+72,y+47,w-72,20,Text.literal("整数数量"));
        quantity.setMaxLength(4);quantity.setText(amount);quantity.setChangedListener(v->{amount=v;token="";total="";if(confirm!=null)confirm.active=false;});addDrawableChild(quantity);
        addDrawableChild(ButtonWidget.builder(Text.literal("预览实际总价"),b->{token="";confirm.active=false;try{int n=Integer.parseInt(quantity.getText());if(n<1||n>2304)throw new IllegalArgumentException();MarketUiClient.action("shopquote "+item+" "+n+" "+(buying?"buy":"sell"));message="正在询价…";}catch(RuntimeException e){message="请输入正整数，不能超过服务器单笔数量限制。";}}).dimensions(x,y+77,w,20).build());
        confirm=addDrawableChild(ButtonWidget.builder(Text.literal("确认交易"),b->{String t=token;token="";confirm.active=false;if(!t.isEmpty()){MarketUiClient.action("shopconfirm "+t);message="正在结算，请勿重复提交。";}}).dimensions(x,y+152,w,20).build());confirm.active=false;
        addDrawableChild(ButtonWidget.builder(Text.literal("返回市场"),b->close()).dimensions(x,y+180,w,20).build());
    }
    @Override public void render(DrawContext c,int mx,int my,float delta) {
        renderBackground(c);
        if(width<300||height<240){c.drawText(textRenderer,"请调小界面尺寸后交易（至少 300 × 240）",8,40,0xffcc77,false);super.render(c,mx,my,delta);return;}
        int w=Math.min(340,width-24),x=(width-w)/2,y=Math.max(12,(height-220)/2);
        c.drawText(textRenderer,title,x,y,0xffffff,false);c.drawText(textRenderer,textRenderer.trimToWidth(name,w),x,y+23,0xcccccc,false);
        c.drawText(textRenderer,"数量（件）",x,y+53,0xffffff,false);
        String line=total.isEmpty()?"尚未确认报价":(buying?"应付 ":"应收 ")+total+" "+currency+" / "+quotedCount+" 件";
        c.drawText(textRenderer,textRenderer.trimToWidth(line,w),x,y+104,0x66ddaa,false);
        var lines=textRenderer.wrapLines(Text.literal(message),w);int yy=y+120;for(int i=0;i<Math.min(2,lines.size());i++){c.drawText(textRenderer,lines.get(i),x,yy,0xffcc77,false);yy+=11;}
        super.render(c,mx,my,delta);
        if(my>=y+104&&my<y+146)c.drawOrderedTooltip(textRenderer,textRenderer.wrapLines(Text.literal(line+"\n"+message),Math.max(120,w)),mx,my);
    }
    @Override public void close(){client.setScreen(parent);MarketUiClient.refresh();MarketUiClient.catalog("note");}
    @Override public boolean shouldPause(){return false;}
}
