package dev.jdguo.market.ui;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

/** Currency directory is independent of supply. Orders always request a server quote first. */
public final class PersonalMarketScreen extends Screen {
    private final Screen back;
    private JsonObject data=new JsonObject();
    private String selected="",quantity="1",filter="",notice="正在同步个人币…",token="",maximum="";
    private boolean pending;
    private int page,ticks,timeout;
    private TextFieldWidget quantityField,searchField;
    private MarketLayout.Layout layout;
    public PersonalMarketScreen(Screen back){super(Text.literal("个人币现货交易"));this.back=back;}
    private static String text(JsonObject row,String key){return MarketScreen.text(row,key);}
    private List<JsonObject> rows(){List<JsonObject> result=new ArrayList<>();if(data.has("notes"))for(var e:data.getAsJsonArray("notes")){var r=e.getAsJsonObject();if(filter.isBlank()||text(r,"name").contains(filter))result.add(r);}return result;}
    private JsonObject selected(){if(data.has("notes"))for(var e:data.getAsJsonArray("notes")){var row=e.getAsJsonObject();if(selected.equals(text(row,"id")))return row;}return null;}
    private void capture(){if(quantityField!=null)quantity=quantityField.getText();if(searchField!=null)filter=searchField.getText();}
    private void rebuild(){if(client!=null){capture();clearChildren();init();}}
    void accept(JsonObject response){
        capture();pending=false;timeout=0;
        if(response.has("error")){token="";notice=text(response,"error");}
        else if(response.has("notes")){data=response;if(selected.isBlank()&&!rows().isEmpty())selected=text(rows().get(0),"id");if(notice.equals("正在同步个人币…"))notice="选择货币 → 输入数量 → 获取报价 → 确认付款。";}
        else if("quote".equals(text(response,"kind"))){
            if(Boolean.parseBoolean(text(response,"ok"))&&selected.equals(text(response,"id"))&&quantity.equals(text(response,"quantity"))){token=text(response,"token");maximum=text(response,"maximum");notice="确认买入「"+text(response,"name")+"」×"+quantity+"，最高支付 "+maximum+" "+central()+"。30 秒内有效。";}
            else{token="";notice="无法报价（"+text(response,"code")+"）：请检查可买数量、交易状态及准备金。";}
        }else if(response.has("completed")){token="";notice=text(response,"message");MarketUiClient.action("notes");}
        rebuild();
    }
    private String central(){String n=text(data,"centralName");return n.isBlank()?"央行券":n;}
    private void request(String action){if(pending)return;if(MarketUiClient.action(action)){pending=true;timeout=100;rebuild();}else notice="未连接市场服务。";}
    @Override protected void init(){
        layout=MarketLayout.at(width,height);quantityField=null;searchField=null;
        button("商品 / 领地",8,6,112,()->{if(client!=null)client.setScreen(back);});button("刷新",width-116,6,50,()->{token="";request("notes");});button("关闭",width-62,6,54,this::close);
        if(!MarketLayout.supported(width,height))return;
        var l=layout.listings();searchField=field(l.x()+5,l.y()+24,l.width()-10,filter,"按名称搜索",64);
        button("搜索",l.x()+5,l.y()+47,l.width()-10,()->{capture();page=0;rebuild();});
        var rows=rows();int capacity=Math.max(1,(l.height()-105)/24);page=Math.min(page,Math.max(0,(rows.size()-1)/capacity));
        for(int i=0;i<capacity&&page*capacity+i<rows.size();i++){var row=rows.get(page*capacity+i);button((selected.equals(text(row,"id"))?"› ":"")+text(row,"name"),l.x()+5,l.y()+72+i*24,l.width()-10,()->{selected=text(row,"id");token="";quantity="1";if(quantityField!=null)quantityField.setText(quantity);rebuild();});}
        button("‹",l.x()+5,l.y()+l.height()-24,30,()->{page=Math.max(0,page-1);rebuild();});button("›",l.x()+l.width()-35,l.y()+l.height()-24,30,()->{page++;rebuild();});
        var o=layout.order();quantityField=field(o.x()+60,o.y()+24,Math.max(80,o.width()-70),quantity,"买入整数数量",64);
        quantityField.setChangedListener(v->{quantity=v;token="";});quantityField.setEditable(!pending);
        var row=selected();
        var buy=button(token.isBlank()?"获取买入报价":"确认付款 "+maximum+" "+central(),o.x()+6,o.y()+57,o.width()-12,()->{
            capture();if(!quantity.matches("[1-9][0-9]{0,63}")){notice="数量必须为正整数。";return;}
            if(token.isBlank())request("notequote "+selected+" "+quantity);else{String t=token;token="";request("notebuy "+t);}
        });buy.active=!pending&&row!=null&&Boolean.parseBoolean(text(row,"tradable"))&&!"0".equals(text(row,"available"));
        button("取消确认",o.x()+6,o.y()+82,o.width()-12,()->{token="";rebuild();}).active=!pending;
    }
    private ButtonWidget button(String label,int x,int y,int width,Runnable action){var b=addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label,Math.max(1,width-8))),ignored->action.run()).dimensions(x,y,width,18).build());b.setTooltip(Tooltip.of(Text.literal(label)));return b;}
    private TextFieldWidget field(int x,int y,int width,String value,String label,int max){var f=new TextFieldWidget(textRenderer,x,y,width,18,Text.literal(label));f.setMaxLength(max);f.setText(value);return addDrawableChild(f);}
    private void line(DrawContext c,String value,int x,int y,int width,int color){c.drawText(textRenderer,textRenderer.trimToWidth(value,Math.max(1,width)),x,y,color,false);}
    private void panel(DrawContext c,MarketLayout.Box b,String title){c.fill(b.x(),b.y(),b.x()+b.width(),b.y()+b.height(),0xff151b25);line(c,title,b.x()+5,b.y()+7,b.width()-10,0xffdde8f2);}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        renderBackground(c);c.fill(0,0,width,height,0xff0c1017);
        if(!MarketLayout.supported(width,height)){line(c,"请降低 GUI 缩放，至少需要 480×300。",8,42,width-16,0xffffc66d);super.render(c,mx,my,delta);return;}
        var l=layout.listings();panel(c,l,"个人币目录");var r=selected();var d=layout.details();panel(c,d,r==null?"请选择个人币":text(r,"name"));
        line(c,"持有: "+text(r,"held"),d.x()+6,d.y()+26,d.width()-12,0xff99abbc);
        line(c,"参考单价: "+(text(r,"price").isBlank()?"暂无牌价":text(r,"price")),d.x()+6,d.y()+41,d.width()-12,0xff19cc9e);
        line(c,"计价: "+central(),d.x()+6,d.y()+56,d.width()-12,0xff99abbc);
        line(c,Boolean.parseBoolean(text(r,"tradable"))?"按实际准备金报价":"交易暂停 / 暂无有效报价",d.x()+6,d.y()+71,d.width()-12,0xffffc66d);
        var b=layout.book();panel(c,b,"真实待售数量");
        line(c,"发行者: "+text(r,"issuerOffer"),b.x()+6,b.y()+26,b.width()-12,0xff99abbc);
        line(c,"可买最大单笔: "+text(r,"available"),b.x()+6,b.y()+41,b.width()-12,0xff19cc9e);
        line(c,"我的挂单: "+text(r,"offered"),b.x()+6,b.y()+56,b.width()-12,0xff99abbc);
        line(c,"不合并多个卖家",b.x()+6,b.y()+71,b.width()-12,0xff99abbc);
        var o=layout.order();panel(c,o,"买入 · "+central()+"余额 "+text(data,"balance"));line(c,"数量",o.x()+6,o.y()+29,50,0xffdde8f2);
        line(c,pending?"等待服务器结果，请勿重复提交。":"无卖盘仍保留币种条目；不能购买自己的卖单。",o.x()+6,o.y()+110,o.width()-12,0xff99abbc);
        line(c,notice,8,height-15,width-16,0xffffc66d);
        super.render(c,mx,my,delta);
    }
    @Override public void tick(){super.tick();if(pending&&--timeout<=0){pending=false;token="";notice="响应超时，请刷新余额确认结果；不要重复付款。";rebuild();}if(++ticks>=100&&!pending&&token.isBlank()&&(quantityField==null||!quantityField.isFocused())&&(searchField==null||!searchField.isFocused())){ticks=0;MarketUiClient.action("notes");}}
    @Override public boolean shouldPause(){return false;}
}
