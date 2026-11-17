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
    private ButtonWidget buyButton;
    private String tender="note";
    private JsonObject quote=new JsonObject();
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
        else if(response.has("notes")){data=response;if(!tenders().contains(tender)){tender="note";invalidate();}if(selected.isBlank()&&!rows().isEmpty())selected=text(rows().get(0),"id");if(notice.equals("正在同步个人币…"))notice="选择货币 → 输入数量 → 获取报价 → 确认付款。";}
        else if("quote".equals(text(response,"kind"))){
            if(!response.has("tender")&&!data.has("paymentProtocol"))response.addProperty("tender","note");
            if(Boolean.parseBoolean(text(response,"ok"))&&selected.equals(text(response,"id"))&&quantity.equals(text(response,"quantity"))&&tender.equals(text(response,"tender"))){quote=response;token=text(response,"token");maximum=text(response,"maximum");notice="买入「"+text(response,"name")+"」×"+quantity+"，支付 "+paid()+" "+tenderName()+"。报价 30 秒内有效。";}
            else{token="";notice="无法报价："+(text(response,"details").isBlank()?reason(text(response,"code")):text(response,"details"));}
        }else if(response.has("completed")){token="";notice=text(response,"message");MarketUiClient.action("notes");}
        rebuild();
    }
    private static String reason(String code){return switch(code){
        case "funds" -> "所选付款币种的银行余额不足。";
        case "custody_funds" -> "银行实物保管余额不足，未扣款。";
        case "insufficient_treasury" -> "央行自有央行券不足以结算或找零，未扣款。";
        case "inexact_tender" -> "当前金额无法在支持的精度内精确结算，请调整数量或付款币种；未扣款。";
        case "unknown_tender" -> "银行版本不支持此付款币种。";
        case "empty" -> "没有足够的可购买卖单。";
        case "self" -> "不能购买自己的卖单。";
        default -> "请检查可买数量、交易状态与准备金（"+code+"）。";
    };}
    private static boolean positive(String value){try{return new java.math.BigDecimal(value).signum()>0;}catch(RuntimeException e){return false;}}
    private List<String> tenders(){
        List<String> list=new ArrayList<>();list.add("note");
        if(data.has("paymentProtocol")){list.add("diamond");list.add("copper");
            if(data.has("personalPayments")&&data.get("personalPayments").getAsBoolean()&&data.has("notes"))
                for(var e:data.getAsJsonArray("notes")){var r=e.getAsJsonObject();if(!selected.equals(text(r,"id"))&&positive(text(r,"held")))list.add("personal:"+text(r,"id"));}
        }return list;
    }
    private String tenderName(){
        if(tender.equals("note"))return central();if(tender.equals("diamond"))return "钻石";if(tender.equals("copper"))return "铜锭";
        if(data.has("notes"))for(var e:data.getAsJsonArray("notes")){var r=e.getAsJsonObject();if(tender.equals("personal:"+text(r,"id")))return text(r,"name");}
        return "个人币";
    }
    private String displayRate(){
        try{String rate=text(quote,"rate");if(rate.isBlank())rate=new java.math.BigDecimal(paid()).divide(new java.math.BigDecimal(quantity),8,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();return "1 "+text(selected(),"name")+" = "+rate+" "+tenderName();}catch(RuntimeException e){return "待报价";}
    }
    private String paid(){String value=text(quote,"paymentAmount");return value.isBlank()?(tender.equals("note")?maximum:text(quote,"physical")):value;}
    private void invalidate(){token="";maximum="";quote=new JsonObject();if(buyButton!=null)buyButton.setMessage(Text.literal("获取买入报价"));}
    private String central(){String n=text(data,"centralName");return n.isBlank()?"央行券":n;}
    private void request(String action){if(pending)return;if(MarketUiClient.action(action)){pending=true;timeout=100;rebuild();}else notice="未连接市场服务。";}
    @Override protected void init(){
        layout=MarketLayout.at(width,height);quantityField=null;searchField=null;
        button("商品 / 领地",8,6,112,()->{if(client!=null)client.setScreen(back);});button("刷新",width-116,6,50,()->{invalidate();request("notes");});button("关闭",width-62,6,54,this::close);
        if(!MarketLayout.supported(width,height))return;
        var l=layout.listings();searchField=field(l.x()+5,l.y()+24,l.width()-10,filter,"按名称搜索",64);
        button("搜索",l.x()+5,l.y()+47,l.width()-10,()->{capture();page=0;rebuild();});
        var rows=rows();int capacity=Math.max(1,(l.height()-105)/24);page=Math.min(page,Math.max(0,(rows.size()-1)/capacity));
        for(int i=0;i<capacity&&page*capacity+i<rows.size();i++){var row=rows.get(page*capacity+i);button((selected.equals(text(row,"id"))?"› ":"")+text(row,"name"),l.x()+5,l.y()+72+i*24,l.width()-10,()->{selected=text(row,"id");invalidate();if(tender.equals("personal:"+selected))tender="note";quantity="1";if(quantityField!=null)quantityField.setText(quantity);rebuild();});}
        button("‹",l.x()+5,l.y()+l.height()-24,30,()->{page=Math.max(0,page-1);rebuild();});button("›",l.x()+l.width()-35,l.y()+l.height()-24,30,()->{page++;rebuild();});
        var o=layout.order();quantityField=field(o.x()+60,o.y()+24,Math.max(80,o.width()-122),quantity,"买入整数数量",64);
        quantityField.setChangedListener(v->{quantity=v;invalidate();});quantityField.setEditable(!pending);
        var row=selected();
        button("付款："+tenderName()+" ▸",o.x()+6,o.y()+48,o.width()-12,()->{
            var choices=tenders();int i=choices.indexOf(tender);tender=choices.get((i+1)%choices.size());invalidate();rebuild();
        }).active=!pending;
        int half=(o.width()-18)/2;
        buyButton=button(token.isBlank()?"获取买入报价":"确认支付 "+paid()+" "+tenderName(),o.x()+6,o.y()+72,half,()->{
            capture();if(!quantity.matches("[1-9][0-9]{0,63}")){notice="数量必须为正整数。";return;}
            if(token.isBlank())request("notequote "+selected+" "+quantity+(data.has("paymentProtocol")?" "+tender:""));else{String t=token;token="";request("notebuy "+t);}
        });buyButton.active=!pending&&row!=null&&Boolean.parseBoolean(text(row,"tradable"))&&!"0".equals(text(row,"available"));
        button("结算明细",o.x()+12+half,o.y()+72,half,()->{
            List<String> lines=new ArrayList<>();
            lines.add(tenderName()+" → "+text(selected(),"name")+"，买入数量 "+quantity);
            if(!quote.entrySet().isEmpty()){
                lines.add("实际扣款："+paid()+" "+tenderName());lines.add("成交汇率："+displayRate());
                lines.add("卖方结算金额："+maximum+" "+central());
                if(PaymentDisplay.positive(text(quote,"change")))lines.add("实退零钱："+text(quote,"change")+" "+central());
                lines.add("兑换费："+(text(quote,"fee").isBlank()?"以服务端结算为准":text(quote,"fee")));
                if(!text(quote,"details").isBlank())lines.add(text(quote,"details"));
            }
            lines.add("实物付款只扣个人银行储蓄中的整数实物，差额退央行券。个人币付款按小数扣款，剩余个人币余额保留原币种；付款部分回收并扣缴准备金，再由真实央行券买入目标币。两段一起成功或一起失败，不需要手动兑换两次。");
            lines.add("卖方收到央行券；买入已发行的币仅转移持有人，不给买入币增加准备金。报价有效期为 30 秒，过期或价格变化须重新询价。");
            if(client!=null)client.setScreen(new PersonalPaymentDetailsScreen(this,lines));
        }).active=!pending;
        if(!token.isBlank())button("取消",o.x()+o.width()-52,o.y()+24,46,()->{invalidate();rebuild();}).active=!pending;

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
        line(c,pending?"等待服务器结果，请勿重复提交。":token.isBlank()?(tender.startsWith("personal:")?"个人币支持小数付款，余额保留原币种。":"实物仅扣银行储蓄整件；必要差额退央行券。"):(PaymentDisplay.positive(text(quote,"change"))?"找零 "+text(quote,"change")+" "+central()+" · ":"")+"余额 "+text(quote,"balance")+" "+tenderName(),o.x()+6,o.y()+98,o.width()-12,0xff99abbc);
        line(c,token.isBlank()?"统一结算给卖家央行券，不改变买入币准备金。":"成交汇率: "+displayRate()+" · 兑换费: "+(text(quote,"fee").isBlank()?"待确认":text(quote,"fee")),o.x()+6,o.y()+113,o.width()-12,0xff99abbc);
        line(c,notice,8,height-15,width-16,0xffffc66d);
        super.render(c,mx,my,delta);
    }
    @Override public void tick(){super.tick();if(pending&&--timeout<=0){pending=false;token="";notice="响应超时，请刷新余额确认结果；不要重复付款。";rebuild();}if(++ticks>=100&&!pending&&token.isBlank()&&(quantityField==null||!quantityField.isFocused())&&(searchField==null||!searchField.isFocused())){ticks=0;MarketUiClient.action("notes");}}
    @Override public boolean shouldPause(){return false;}
}
