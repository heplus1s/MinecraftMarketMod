package dev.jdguo.market.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Independent product quotes, real offers, and explicit physical-item spot orders. */
public final class MarketScreen extends Screen {
    private JsonObject data;
    private MarketLayout.Layout layout;
    private String selectedItem="",selectedOffer="",currency="",price="1",count="64",buyCount="1",notice="",filter="",percent="100",category="全部";
    private String catalogCurrency="";
    private int page,bookPage,refreshTicks;
    private boolean mine,confirming,pending,linked,landMode;
    private TextFieldWidget priceField,countField,buyField,filterField,percentField;
    private final List<JsonObject> catalog=new ArrayList<>(),rows=new ArrayList<>();
    public MarketScreen(JsonObject data){super(Text.literal("市场现货交易"));this.data=data;}
    static String text(JsonObject o,String key){try{return o!=null&&o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}catch(RuntimeException e){return "";}}
    private List<JsonObject> array(String key){List<JsonObject> r=new ArrayList<>();if(data.has(key)&&data.get(key).isJsonArray())for(JsonElement v:data.getAsJsonArray(key))if(v.isJsonObject())r.add(v.getAsJsonObject());return r;}
    private String currencyLabel(String id){for(var c:array("currencies"))if(id.equals(text(c,"id")))return text(c,"label");return "note".equals(id)?"央行券":id.startsWith("personal:")?"个人币（名称待同步）":id;}
    private String name(JsonObject row){String n=text(row,"itemName");String key=text(row,"translationKey");if(!Boolean.parseBoolean(text(row,"hasVariant"))&&!key.isBlank())n=Text.translatable(key).getString();return n.isBlank()?text(row,"itemId"):n;}
    private boolean own(JsonObject row){return row!=null&&client!=null&&client.player!=null&&client.player.getUuidAsString().equals(text(row,"sellerId"));}
    void update(JsonObject next){capture();JsonObject previous=offer();data=next;if(!Objects.equals(previous,offer()))confirming=false;if(!editing())rebuild();}
    void catalog(String denomination,List<JsonObject> items){if(!currency.isBlank()&&!currency.equals(denomination))return;capture();catalogCurrency=denomination;catalog.clear();catalog.addAll(items);if(!editing())rebuild();}
    void result(JsonObject result){pending=false;confirming=false;notice="";if(result.has("lines"))for(var line:result.getAsJsonArray("lines"))notice+=line.getAsString()+" ";MarketUiClient.refresh();rebuild();}
    private void capture(){if(priceField!=null)price=priceField.getText();if(countField!=null)count=countField.getText();if(buyField!=null)buyCount=buyField.getText();if(filterField!=null)filter=filterField.getText();if(percentField!=null)percent=percentField.getText();}
    private void rebuild(){if(client!=null){clearChildren();init();}}
    @Override protected void init(){
        layout=MarketLayout.at(width,height);priceField=null;countField=null;buyField=null;filterField=null;percentField=null;
        if(!MarketLayout.supported(width,height)){button("关闭",Math.max(4,width-62),8,54,this::close);return;}
        if(currency.isBlank())currency=text(data,"defaultCurrency");if(currency.isBlank())currency="note";
        button(landMode?"领地 → 个人币":"商品 → 领地",8,6,150,()->{capture();if(landMode){landMode=false;client.setScreen(new PersonalMarketScreen(this));MarketUiClient.action("notes");return;}landMode=true;selectedItem="";selectedOffer="";category="全部";filter="";page=0;bookPage=0;confirming=false;rebuild();});
        button("计价: "+currencyLabel(currency),Math.max(160,width-300),6,176,()->{
            capture();var cs=array("currencies");if(cs.isEmpty())return;int i=0;for(;i<cs.size();i++)if(currency.equals(text(cs.get(i),"id")))break;
            currency=text(cs.get((i+1)%cs.size()),"id");selectedOffer="";confirming=false;bookPage=0;catalogCurrency="";MarketUiClient.catalog(currency);rebuild();
        });
        button("刷新",width-116,6,50,this::refresh);button("关闭",width-62,6,54,this::close);
        rows.clear();for(var row:products()){String id=key(row);boolean listedByMe=false;for(var l:array("listings"))if(id.equals(key(l))&&own(l)){listedByMe=true;break;}
            if((!mine||listedByMe)&&("全部".equals(category)||category.equals(text(row,"category")))&&(filter.isBlank()||(name(row)+id).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))))rows.add(row);
        }
        if(selectedItem.isBlank()&&!rows.isEmpty())selectedItem=key(rows.get(0));
        listingPane();bookPane();orderPane();
    }
    private void refresh(){capture();confirming=false;MarketUiClient.refresh();MarketUiClient.catalog(currency);refreshTicks=0;}
    @Override public void tick(){super.tick();if(++refreshTicks>=100&&!pending&&!confirming&&!editing()){refreshTicks=0;MarketUiClient.refresh();MarketUiClient.catalog(currency);}}
    private boolean editing(){return focused(filterField)||focused(priceField)||focused(countField)||focused(buyField)||focused(percentField);}
    private static boolean focused(TextFieldWidget field){return field!=null&&field.isFocused();}
    private void listingPane(){var b=layout.listings();filterField=field(b.x()+5,b.y()+21,b.width()-10,filter,"搜索名称或物品 ID",64);
        button("分类："+categoryLabel(category),b.x()+5,b.y()+43,b.width()-10,()->{capture();client.setScreen(new CategoryScreen(this));});
        button(mine?"我的挂牌品种":"全部品种",b.x()+5,b.y()+65,b.width()-10,()->{capture();mine=!mine;page=0;rebuild();});
        int capacity=listCapacity();page=Math.min(page,Math.max(0,(rows.size()-1)/capacity));
        for(int i=0;i<capacity&&page*capacity+i<rows.size();i++){var r=rows.get(page*capacity+i);var productButton=button((selectedItem.equals(key(r))?"› ":"")+name(r),b.x()+5,b.y()+88+i*32,b.width()-10,()->selectProduct(r));if(landMode)productButton.setTooltip(Tooltip.of(Text.literal(name(r)+"\n"+landScope(r)+"\n总价: "+text(r,"totalPrice")+" "+currencyLabel(text(r,"currency")))));}
        int y=b.y()+b.height()-23;button("‹",b.x()+5,y,23,()->{capture();page=Math.max(0,page-1);rebuild();});button("搜索",b.x()+31,y,b.width()-62,()->{capture();page=0;rebuild();});button("›",b.x()+b.width()-28,y,23,()->{capture();page++;rebuild();});
    }
    private String categoryLabel(String key){return MarketCategories.label(key);}
    /** Direct category selection instead of cycling through every category. */
    private final class CategoryScreen extends Screen {
        private final Screen parent;
        private int categoryPage;
        private CategoryScreen(Screen parent){super(Text.literal("商品分类"));this.parent=parent;}
        @Override protected void init(){
            List<String> available=new ArrayList<>();available.add("全部");
            for(String id:MarketCategories.ORDER)if(!"全部".equals(id)&&products().stream().anyMatch(r->id.equals(text(r,"category"))))available.add(id);
            for(var r:products()){String id=text(r,"category");if(!id.isBlank()&&!available.contains(id))available.add(id);}
            int columns=width>=360?3:2, gap=6, cell=Math.min(140,(width-24-gap*(columns-1))/columns);
            int rows=Math.max(1,(height-110)/26),capacity=columns*rows;
            categoryPage=Math.min(categoryPage,Math.max(0,(available.size()-1)/capacity));
            int left=(width-(cell*columns+gap*(columns-1)))/2;
            for(int n=0;n<capacity&&categoryPage*capacity+n<available.size();n++){
                String id=available.get(categoryPage*capacity+n);
                addDrawableChild(ButtonWidget.builder(Text.literal((category.equals(id)?"› ":"")+categoryLabel(id)),button->{category=id;page=0;confirming=false;client.setScreen(parent);}).dimensions(left+(n%columns)*(cell+gap),40+(n/columns)*26,cell,20).build());
            }
            if(available.size()>capacity){
                var prev=addDrawableChild(ButtonWidget.builder(Text.literal("上一页"),button->{categoryPage--;clearChildren();init();}).dimensions(left,height-62,cell,20).build());prev.active=categoryPage>0;
                var next=addDrawableChild(ButtonWidget.builder(Text.literal("下一页"),button->{categoryPage++;clearChildren();init();}).dimensions(left+cell+gap,height-62,cell,20).build());next.active=(categoryPage+1)*capacity<available.size();
            }
            addDrawableChild(ButtonWidget.builder(Text.literal("返回市场"),button->close()).dimensions(left,height-32,cell*columns+gap*(columns-1),20).build());
        }
        @Override public void close(){client.setScreen(parent);}
        @Override public void render(DrawContext c,int x,int y,float delta){renderBackground(c);c.drawCenteredTextWithShadow(textRenderer,title,width/2,16,0xffffff);super.render(c,x,y,delta);}
    }

    private int listCapacity(){return Math.max(1,(layout.listings().height()-118)/32);}
    private void selectProduct(JsonObject row){capture();selectedItem=key(row);if(landMode){currency=text(row,"currency");MarketUiClient.catalog(currency);}selectedOffer="";buyCount="1";confirming=false;bookPage=0;rebuild();}
    private void selectOffer(JsonObject row){capture();selectedItem=key(row);selectedOffer=text(row,"id");buyCount="1";confirming=false;rebuild();}
    private static boolean item(JsonObject row){return row!=null&&!text(row,"itemId").isBlank();}
    private static String landScope(JsonObject row){
        int chunks=0;
        try{chunks=Integer.parseInt(text(row,"chunkCount"));}catch(RuntimeException ignored){}
        return LandScopeLabels.label(text(row,"kind"),chunks);
    }
    private static String key(JsonObject row){return item(row)?text(row,"itemId"):"land:"+text(row,"id");}
    private List<JsonObject> products(){List<JsonObject> all=new ArrayList<>();if(!landMode)all.addAll(catalog);for(var row:array("listings"))if(landMode&&!item(row)){var copy=row.deepCopy();copy.addProperty("category","land");all.add(copy);}return all;}
    private JsonObject product(){for(var r:products())if(selectedItem.equals(key(r)))return r;return null;}
    private JsonObject offer(){for(var r:array("listings"))if(selectedOffer.equals(text(r,"id"))&&selectedItem.equals(key(r))&&currency.equals(text(r,"currency")))return r;return null;}
    private List<JsonObject> offers(){List<JsonObject> list=new ArrayList<>();for(var r:array("listings"))if(!selectedItem.isBlank()&&selectedItem.equals(key(r))&&currency.equals(text(r,"currency")))list.add(r);list.sort((a,b)->{try{return new BigInteger(text(a,"unitPrice")).compareTo(new BigInteger(text(b,"unitPrice")));}catch(RuntimeException e){return 0;}});return list;}
    private int bookCapacity(){return Math.max(1,(layout.book().height()-(landMode?63:87))/23);}
    private void bookPane(){var b=layout.book();
        if(!landMode){var p=product();boolean enabled=Boolean.parseBoolean(text(data,"systemShop"))&&"note".equals(currency)&&p!=null;
            var ask=button("系统卖出 "+quote(p,"shopAsk"),b.x()+5,b.y()+24,b.width()-10,()->client.setScreen(new SystemShopScreen(this,selectedItem,name(p),true)));
            var bid=button("系统收购 "+quote(p,"shopBid"),b.x()+5,b.y()+47,b.width()-10,()->client.setScreen(new SystemShopScreen(this,selectedItem,name(p),false)));
            ask.active=enabled&&!text(p,"shopAsk").isBlank();bid.active=enabled&&!text(p,"shopBid").isBlank();
            ask.setTooltip(Tooltip.of(Text.literal("系统商店实时报价，点击预览实际总额；无虚构库存数量")));
            bid.setTooltip(Tooltip.of(Text.literal("系统账户付款；只收全新普通物品，受余额与单笔数量限制")));
        }
        int capacity=bookCapacity();var offers=offers();bookPage=Math.min(bookPage,Math.max(0,(offers.size()-1)/capacity));
        for(int i=0;i<capacity&&bookPage*capacity+i<offers.size();i++){var r=offers.get(bookPage*capacity+i);String mark=Boolean.parseBoolean(text(r,"hasVariant"))?"*":"";var button=button((selectedOffer.equals(text(r,"id"))?"› ":"")+(landMode?landScope(r)+" · "+text(r,"totalPrice"):text(r,"unitPrice")+" ×"+text(r,"count")+mark),b.x()+5,b.y()+(landMode?24:68)+i*23,b.width()-10,()->selectOffer(r));button.setTooltip(Tooltip.of(Text.literal(name(r)+"\n卖家: "+text(r,"sellerName")+"\n"+attributes(r)+(landMode?"\n总价: "+text(r,"totalPrice")+" "+currencyLabel(text(r,"currency")):"")+"\n点击选择这笔卖单")));}
        int y=b.y()+b.height()-24;button("‹",b.x()+5,y,24,()->{capture();bookPage=Math.max(0,bookPage-1);rebuild();});button("›",b.x()+33,y,24,()->{capture();bookPage++;rebuild();});
    }
    private String attributes(JsonObject row){if(row==null)return "未选卖单";if(!item(row))return landScope(row)+" · "+text(row,"parcelName");List<String> parts=new ArrayList<>();if(Boolean.parseBoolean(text(row,"enchanted")))parts.add("已附魔");if(!text(row,"maxDamage").isBlank()&&!"0".equals(text(row,"maxDamage")))parts.add("损耗 "+text(row,"damage")+"/"+text(row,"maxDamage"));if(Boolean.parseBoolean(text(row,"hasVariant")))parts.add("特殊属性（独立库存）");return parts.isEmpty()?"普通物品":String.join(" · ",parts);}
    private void orderPane(){var b=layout.order();int gap=8,w=(b.width()-20)/2,x=b.x()+5,sx=x+w+gap,y=b.y();var row=offer();
        if(!landMode){buyField=field(x+28,y+29,w-28,buyCount,"买入数量（件）",4);buyField.setChangedListener(v->{buyCount=v;confirming=false;});buyField.setEditable(item(row)&&!pending);}
        var buy=button(own(row)?"撤销这笔挂牌":confirming?"确认付款":"买入选中卖单",x,y+107,w,()->{
            capture();JsonObject current=offer();if(current==null||pending)return;if(own(current)){send("cancel "+text(current,"id"));return;}
            try{int n=item(current)?Integer.parseInt(buyCount):1;if(n<=0||(item(current)&&n>Integer.parseInt(text(current,"count"))))throw new IllegalArgumentException();BigInteger total=item(current)?new BigInteger(text(current,"unitPrice")).multiply(BigInteger.valueOf(n)):new BigInteger(text(current,"totalPrice"));
                if(!confirming){confirming=true;notice=(item(current)?"确认买入 "+n+" 件 "+name(current)+"（"+attributes(current)+"）":"确认购买 "+landScope(current)+" · "+name(current)+"，按挂牌范围整笔成交")+"，最高支付 "+total+" "+currencyLabel(currency)+"。";rebuild();return;}
                send("buy "+selectedOffer+" "+total+(item(current)?" "+n:""));
            }catch(RuntimeException ex){notice="数量须为正整数且不超过卖单剩余量；无报价不能成交。";confirming=false;}});buy.active=row!=null&&!pending;
        if(landMode){
            if(client.player!=null&&client.player.hasPermissionLevel(2)) {
                var repurchase = button("系统回购",sx,y+20,w,()->{
                    var screen = new GovernmentRepurchaseClientScreen(this);
                    client.setScreen(screen);
                    screen.load();
                });
                repurchase.active=Boolean.parseBoolean(text(data,"nativeSystemRepurchase"))&&!pending;
                repurchase.setTooltip(Tooltip.of(Text.literal(repurchase.active
                        ?"查看玩家以央行券挂牌的领地；确认前不会扣款"
                        :"原生回购页面需要更新市场模组服务端")));
                button("个人 → 服务器用地",sx,y+52,w,()->{
                    if(client.getNetworkHandler()!=null){client.setScreen(null);client.getNetworkHandler().sendChatCommand("market land servermode");}
                }).setTooltip(Tooltip.of(Text.literal("OP 2：管理脚下服务器宗地；普通市场买入仍使用个人账户")));
            }
            var sellLand=button("出售脚下整宗 · 推荐价",sx,y+107,w,()->{
                MarketUiClient.action("landform"); notice="正在打开出售表单，推荐价格可修改；请站在自己的领地内。";
            }); sellLand.active=!pending;
            return;
        }
        button(linked?"比例 %":"单价",sx,y+29,52,()->{capture();linked=!linked;rebuild();});
        if(linked)percentField=field(sx+56,y+29,w-56,percent,"商店参考价百分比",4);else priceField=field(sx+56,y+29,w-56,price,"每件价格",18);
        countField=field(sx+28,y+54,w-28,count,"挂牌数量（与主手同属性）",4);
        var sell=button("托管并挂牌",sx,y+107,w,()->{capture();try{int n=Integer.parseInt(count);long p=Long.parseLong(linked?percent:price);if(n<1||n>2304||p<1||(linked&&p>1000))throw new IllegalArgumentException();send(linked?"listshop "+n+" "+currency+" "+p:"list "+p+" "+n+" "+currency);}catch(RuntimeException ex){notice="数量 1–2304；单价为正整数；商店比例 1–1000%。无参考价请用固定价。";}});sell.active=!pending;
    }
    private void send(String action){if(MarketUiClient.action(action)){pending=true;confirming=false;notice="已提交，请勿重复操作。";rebuild();}else notice="未连接市场服务，操作未发送。";}
    private ButtonWidget button(String label,int x,int y,int w,Runnable action){var b=addDrawableChild(ButtonWidget.builder(Text.literal(fit(label,w-8)),ignored->action.run()).dimensions(x,y,Math.max(1,w),18).build());b.setTooltip(Tooltip.of(Text.literal(label)));return b;}
    private TextFieldWidget field(int x,int y,int w,String value,String label,int max){var f=new TextFieldWidget(textRenderer,x,y,Math.max(1,w),18,Text.literal(label));f.setMaxLength(max);f.setText(value);addDrawableChild(f);return f;}
    private String fit(String value,int w){return textRenderer.trimToWidth(value,Math.max(1,w));}
    private void line(DrawContext c,String value,int x,int y,int w,int color){c.drawText(textRenderer,fit(value,w),x,y,color,false);}
    private void panel(DrawContext c,MarketLayout.Box b,String title){c.fill(b.x(),b.y(),b.x()+b.width(),b.y()+b.height(),0xff151b25);line(c,title,b.x()+5,b.y()+7,b.width()-10,0xffdde8f2);}
    private String quote(JsonObject row,String key){return currency.equals(catalogCurrency)?empty(text(row,key)):"同步中";}
    private void chart(DrawContext c,MarketLayout.Box b){JsonObject chosen=offer();String variant=chosen==null?"default":text(chosen,"variantKey");List<Double> prices=new ArrayList<>();
        for(var t:array("trades"))if(selectedItem.equals(text(t,"itemId"))&&currency.equals(text(t,"currency"))&&variant.equals(text(t,"variantKey")))try{double p=Double.parseDouble(text(t,"amount"))/Integer.parseInt(text(t,"count"));if(Double.isFinite(p))prices.add(p);}catch(RuntimeException ignored){}
        int x=b.x()+6,y=b.y()+64,w=b.width()-12,h=b.height()-81;if(h<20)return;c.fill(x,y,x+w,y+h,0xff0c1017);
        for(int i=1;i<4;i++){int yy=y+h*i/4;c.fill(x,yy,x+w,yy+1,0xff202b36);}
        if(prices.size()<2){line(c,prices.isEmpty()?"暂无真实成交记录":"仅 1 笔成交，暂不连线",x+5,y+5,w-10,0xff8f9baa);line(c,"初始价来自商店；尚无成交不画线",x+5,y+17,w-10,0xff8f9baa);return;}
        double min=prices.stream().mapToDouble(Double::doubleValue).min().orElse(0),max=prices.stream().mapToDouble(Double::doubleValue).max().orElse(1),span=Math.max(0.0001,max-min);int px=0,py=0;
        for(int i=0;i<prices.size();i++){int xx=x+i*(w-1)/(prices.size()-1),yy=y+h-4-(int)((prices.get(i)-min)/span*(h-8));if(i>0){int steps=Math.max(1,Math.abs(xx-px));for(int k=0;k<=steps;k++){int dx=px+(xx-px)*k/steps,dy=py+(yy-py)*k/steps;c.fill(dx,dy,dx+1,dy+1,0xff19cc9e);}}px=xx;py=yy;}
        line(c,"成交序列 · 非 K 线 · "+prices.size()+" 笔",x,y+h+3,w,0xff8f9baa);
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){renderBackground(c);c.fill(0,0,width,height,0xff0c1017);
        if(!MarketLayout.supported(width,height)){line(c,"请降低 GUI 缩放，至少需要 480×300。",8,42,width-16,0xffffc66d);line(c,"保留品种、行情、盘口和交易区同屏显示。",8,58,width-16,0xff99abbc);super.render(c,mx,my,delta);return;}

        var l=layout.listings();panel(c,l,landMode?"领地挂牌":"品种行情");int capacity=listCapacity();for(int i=0;i<capacity&&page*capacity+i<rows.size();i++){var r=rows.get(page*capacity+i);line(c,landMode?landScope(r)+" · "+text(r,"totalPrice")+" "+currencyLabel(text(r,"currency")):"商店卖 "+quote(r,"shopAsk"),l.x()+7,l.y()+108+i*32,l.width()-14,0xff8f9baa);}if(rows.isEmpty())line(c,landMode?"暂无匹配领地挂牌":catalog.isEmpty()?"正在同步品种…":"无匹配品种",l.x()+5,l.y()+91,l.width()-10,0xff8f9baa);
        var p=product();var d=layout.details();panel(c,d,p==null?"请选择品种":name(p));
        if(landMode){
            line(c,"挂牌人: "+text(p,"sellerName"),d.x()+6,d.y()+25,d.width()-12,0xff99abbc);
            line(c,"维度: "+text(p,"dimension"),d.x()+6,d.y()+39,d.width()-12,0xff99abbc);
            String chunks=p!=null&&p.has("chunks")?p.get("chunks").toString():"—";
            line(c,"区块位置: "+chunks,d.x()+6,d.y()+53,d.width()-12,0xff99abbc);
            String area="—";try{area=Long.parseLong(text(p,"chunkCount"))*256+" 方块²";}catch(RuntimeException ignored){}
            line(c,"面积: "+area,d.x()+6,d.y()+67,d.width()-12,0xff99abbc);
            line(c,p==null?"请选择领地挂牌":landScope(p)+" · 挂牌范围整笔转让",d.x()+6,d.y()+81,d.width()-12,0xff19cc9e);
        }else{line(c,"商店收购 "+quote(p,"shopBid"),d.x()+6,d.y()+25,d.width()-12,0xff19cc9e);line(c,"商店出售 "+quote(p,"shopAsk"),d.x()+6,d.y()+39,d.width()-12,0xfff05a71);line(c,"参考价 · "+currencyLabel(currency),d.x()+6,d.y()+51,d.width()-12,0xff8f9baa);chart(c,d);}

        var b=layout.book();panel(c,b,landMode?"转让挂牌 / 总价":"系统报价 / 玩家卖盘");if(offers().isEmpty())line(c,"暂无玩家卖单",b.x()+6,b.y()+(landMode?28:76),b.width()-12,0xff8f9baa);line(c,"玩家卖盘",b.x()+61,b.y()+b.height()-19,b.width()-66,0xff8f9baa);
        var o=layout.order();panel(c,o,"");int w=(o.width()-20)/2,x=o.x()+5,sx=x+w+8,y=o.y();c.fill(sx-5,y+5,sx-4,y+o.height()-5,0xff2b3543);
        line(c,"买入 / 选中卖单",x,y+8,w,0xff19cc9e);line(c,landMode?"出售 / 脚下整宗领地":"卖出 / 主手物品",sx,y+8,w,0xfff05a71);line(c,landMode?(offer()==null?"请选择挂牌范围":landScope(offer())):"数量",x,y+34,landMode?w:27,0xff99abbc);if(landMode)line(c,"打开表单填写售价",sx,y+40,w,0xff99abbc);else line(c,"数量",sx,y+59,27,0xff99abbc);
        var row=offer();String total="—";try{if(row!=null)total=item(row)?new BigInteger(text(row,"unitPrice")).multiply(new BigInteger(buyCount)).toString():text(row,"totalPrice");}catch(RuntimeException ignored){}
        line(c,"合计 "+total+" "+currencyLabel(currency),x,y+55,w,0xff19cc9e);line(c,row==null?"请点击右上方卖单":attributes(row),x,y+71,w,0xff99abbc);line(c,landMode?"领地按整笔购买":"银行扣款 · 可部分成交",x,y+89,w,0xff99abbc);
        String held=client!=null&&client.player!=null?client.player.getMainHandStack().getName().getString():"—";line(c,landMode?"请站在自己拥有的领地内":"当前主手: "+held,sx,y+78,w,0xff99abbc);line(c,(landMode?"整宗总价 · ":"同属性库存 · ")+currencyLabel(currency),sx,y+91,w,0xff99abbc);
        line(c,notice.isBlank()?(landMode?"领地按挂牌范围整笔成交；整宗或选区以所选卖单为准，服务端复核所有权。":"目录无挂单也有行情 · * 为特殊属性卖单（悬停查看） · 每 5 秒刷新"):notice,7,height-15,width-14,0xffffc66d);super.render(c,mx,my,delta);
        if(mx>=b.x()+61&&mx<b.x()+b.width()&&my>=b.y()+b.height()-24&&my<b.y()+b.height())c.drawOrderedTooltip(textRenderer,textRenderer.wrapLines(Text.literal("系统买卖报价可点击成交；玩家区只展示真实卖单，尚未开放玩家买入委托。"),180),mx,my);
        if(landMode&&p!=null&&p.has("chunks")&&mx>=d.x()&&mx<d.x()+d.width()&&my>=d.y()+53&&my<d.y()+65)c.drawOrderedTooltip(textRenderer,textRenderer.wrapLines(Text.literal("区块位置: "+p.get("chunks")+(Boolean.parseBoolean(text(p,"chunksTruncated"))?"（仅显示前 64 个区块）":"")),Math.max(120,width-48)),mx,my);
        if(!notice.isBlank()&&my>=height-22)c.drawOrderedTooltip(textRenderer,textRenderer.wrapLines(Text.literal(notice),Math.max(120,width-48)),mx,my);
    }
    private static String empty(String s){return s.isBlank()?"无参考价":s;}
    @Override public void resize(net.minecraft.client.MinecraftClient client,int width,int height){capture();super.resize(client,width,height);}
    @Override public boolean shouldPause(){return false;}
    @Override public void removed(){capture();}
}
