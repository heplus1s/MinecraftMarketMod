# MinecraftMarketMod

Fabric 1.20.1 市场模组：玩家挂牌交易物品和领地。挂牌可以参考商店当时的浮动价格，也允许自定义价格。玩家之间成交后，货款给卖方，不进央行。

A Fabric 1.20.1 marketplace for player listings of items and land. Listings can use the shop floating price as a reference, and custom prices are allowed. Player-to-player settlement goes to the seller, not the central bank.

物品挂牌一次买下一整笔。领地可以卖整块宗地，也可以从宗地里拆出若干原版 16×16 区块。不能卖比区块更小的一块方块。银行券的交易市场仍在银行模组里，这里不重复做。

Item listings are bought in full. Land can be a whole parcel, or some vanilla 16 by 16 chunks split out of a parcel. Nothing smaller than a chunk is sold. Bank-note trading stays in the bank mod.

当前先提供指令。箱子界面这次不做，但会预留接口，以后可以直接接上。可选的独立客户端界面以后放在 MinecraftUIMod。不装界面包也可以进服。原版客户端也能进开了这个模组的专用服务器。

Commands come first. A chest GUI is not included yet, but the hook is reserved. Optional client UI will live in MinecraftUIMod. Vanilla clients can still join a dedicated server.

许可证为 MIT，版权归 heplus1s。

## 指令

聊天前缀是 `[市场]`。指令本身是英文。下面的说明是中文。

The chat prefix is `[市场]`. The commands are English.

| 指令 | 作用 |
| --- | --- |
| `/market` | 显示英文用法 |
| `/market list <price> [count]` | 把手上的物品按自定义价格挂出。价格是每一件的价格。不写数量就挂出手上这一整组 |
| `/market list shop [count]` | 同样挂出手上的物品，价格用商店当时的参考价格，购买时再读一次 |
| `/market buy <id>` | 买下整笔挂牌。物品和领地都用这一条 |
| `/market cancel <id>` | 卖方撤销自己的挂牌。管理员可以撤销别人的 |
| `/market search [page]` | 查看正在出售的挂牌 |
| `/market search item <item> [page]` | 按物品查找。可以写 `diamond`，也可以写带引号的 `"minecraft:diamond"` |
| `/market search player <name> [page]` | 按卖方名字查找 |
| `/market search land [page]` | 只看领地 |
| `/market search mine [page]` | 只看自己的挂牌 |
| `/market info <id>` | 查看一块挂牌的编号、价格和位置 |
| `/market price` | 查看手上物品的商店参考价格 |
| `/market reload` | 管理员重新读取配置。已经挂出的价格不变 |
| `/market land list parcel <price>` | 把脚下整块宗地挂出。这是整笔价格，不是每个区块的价格 |
| `/market land list chunk <price>` | 把脚下这一个区块挂出。如果宗地不止一块，成交时会把这一块拆出去 |
| `/market land list chunks <price> <x1> <z1> <x2> <z2>` | 挂出一个矩形里的区块。`x` 和 `z` 是区块坐标，不是方块坐标。负数向下取整，方块 -1 属于区块 -1 |
| `/market land server parcel <price>` | 管理员把脚下整块宗地作为服务器出让挂出 |
| `/market land server chunk <price>` | 管理员把脚下这一个区块作为服务器出让挂出 |
| `/market land server chunks <price> <x1> <z1> <x2> <z2>` | 管理员把矩形里的区块作为服务器出让挂出 |
| `/market land search [page]` | 只看领地挂牌 |

编号可以写前 8 位，也可以写完整编号。一次只能买下一整笔挂牌。

An id can be the first 8 characters or the full id. Each listing is purchased in full.

## 谁收到货款

玩家卖给玩家时，货款从买方的储蓄账户划到卖方的储蓄账户。中央银行不留下这笔钱。发行准备金和商店经营账户也不动。

When one player buys from another, the price moves from the buyer's savings to the seller's savings. The central bank does not keep it. Issuance reserves and the shop operating account stay where they are.

服务器出让土地时，货款进入土地出让账户。它是储蓄总池里的一个分户，和普通储蓄一样计算利息。它不是商店经营账户，不是发行准备金，也不是另开的第三个池子。只有服务器卖地，以及以后处理收回来的土地，才把钱放进这个账户。玩家之间的土地买卖仍然是双方储蓄之间的划转。

When the server sells land, the price goes to the land-grant account. That account is one savings sub-account inside the main savings pool, and it earns interest the same way personal savings do. It is not the shop operating account, not issuance reserves, and not a separate third pool. Only server land sales, and later disposal of recovered land, credit it. Ordinary land trades between players remain a private savings transfer.

挂牌时，管理员必须自己持有这块地。成交后地交给买方，钱不进入管理员个人的储蓄账户。

The operator who lists server land must hold that land. After the sale the buyer receives the land, and the operator's personal savings are not credited.

## 商店参考价格

商店模组如果提供了当前价格，`/market list shop` 和 `/market price` 会读取它。购买按商店参考价格挂出的物品时，用的是成交那一刻的价格，不是挂牌那一刻的价格。

If the shop mod provides a live price, `/market list shop` and `/market price` read it. A shop-priced listing is charged at the price at the moment of purchase.

商店还没有做好，或者这件物品没有参考价格时，自定义价格照常可用。这时不能用商店参考价格成交，已经挂出的这块会保留，并提示你改用自定义价格。

While the shop is absent, or an item has no reference price, custom prices still work. A shop-priced listing is kept and the purchase is refused until a price exists.

商店可以在启动时调用 `dev.jdguo.market.api.ShopPriceApi.install`，也可以提供静态方法 `dev.jdguo.shop.api.ShopPrices.priceEach(物品编号, 币种)`。币种用银行里的编号，默认是 `note`（银行券）。结算币种是钻石时，如果银行里已经安装了钻石报价，市场也会拿来当作参考。带小数的报价不用作整数货款。

## 领地怎么过户

市场不自己改领地存档。买下整块宗地时调用领地模组的 `transferParcel`。买下其中若干区块时调用 `transferChunks`，由领地模组把这些区块拆出去再交给买方。传过去的单号是 `sale:` 加上这块市场挂牌的编号，用来和赠送分开。

The market does not edit land saves itself. A whole parcel uses the land mod's `transferParcel`. Selected chunks use `transferChunks`, which splits those chunks off and gives them to the buyer. The reference is `sale:` plus the market listing id, so a purchase can be told apart from a gift.

挂牌期间，这块宗地不能赠送，不能放弃，不能合并，也不能并入新区块。领地模组里那份不付款的出售，也不能把这块地转走。撤销挂牌或在市场买下之后，这些限制解除。贷款收回土地的接口仍然放行，收回后对应的市场挂牌会取消。

While a parcel is listed it cannot be given away, abandoned, merged, or extended. The land mod's unpaid sale cannot transfer it either. Canceling the listing, or buying it here, lifts the lock. Collateral seizure stays allowed, and the market listing is then removed.

领地模组没有加载时，物品挂牌仍然可用。领地挂牌和购买会说明现在不能转让。

Item listings still work when the land mod is absent. Land listings and purchases explain that the transfer cannot be done.

## 银行还缺的接口

现在的银行模组能记储蓄，但还没有「从一个玩家的储蓄划到另一个玩家」的公开接口，也还没有土地出让账户。市场不会去改银行的内部账本。

The bank can record savings, but it does not yet offer a public player-to-player savings transfer, and it does not yet keep the land-grant account. This market does not write the bank's private books.

需要银行增加 `dev.jdguo.bank.api.MarketSettlement`，里面三个静态方法都返回银行现有的 `TxResult`：

- `transfer(买方, 卖方, 币种, 金额, 单号)`：买方储蓄减少，卖方储蓄增加。储蓄总池的总额不变。
- `creditLandGrant(付款人, 币种, 金额, 单号)`：付款人储蓄减少，土地出让账户增加。这个账户按个人储蓄的利率计息。
- `refundLandGrant(付款人, 币种, 金额, 单号)`：把上一笔退回付款人。

成交单号是 `sale:` 加挂牌编号。退款单号是 `refund:` 加结算编号。余额不够时返回 `insufficient_funds`，并且不能只扣一部分。返回之前要把账本保存好。

银行也可以直接调用本模组的 `BankSettlement.install` 和 `LandGrantAccount.install`，效果相同。接口还没接上时，相关指令会用中文说明哪一笔结算做不了，已经挂出的物品和领地不会被删掉。

## 以后的箱子界面和客户端界面

箱子界面的稳定入口是 `dev.jdguo.market.ui.MarketChestUiHooks`。以后的界面只要调用这里的浏览、购买、撤销和挂牌方法，不用再写一套成交。这个模组现在不打开箱子，也不注册箱子界面。

The reserved chest entry is `dev.jdguo.market.ui.MarketChestUiHooks`. A later menu calls those methods and does not perform settlement itself. This build opens no chest and registers no screen handler.

独立客户端以后放在 MinecraftUIMod。它先向 `marketmod:hello` 报一个版本号，再通过 `marketmod:action` 发送和指令相同的操作。服务端只在客户端声明过频道之后，才从 `marketmod:sync` 和 `marketmod:result` 回传。原版客户端不声明这些频道，所以不会收到它们，进服不受影响。

## 配置和存档

配置文件是 `config/marketmod.json`。

- `currency`：结算币种，默认 `note`，也就是银行券。
- `maxListingsPerPlayer`：每个玩家同时能挂出的数量，默认 24。
- `pageSize`：搜索每页条数，默认 6。
- `maxChunksPerListing`：一次领地挂牌最多多少个区块，默认 64。
- `serverLandSales`：是否允许服务器出让土地，默认开启。

挂牌、托管中的物品、还没交付的物品和未完成的结算都写在主世界的 `data/marketmod.dat`。以后升级会保留已经认识的内容，不认识的字段也会原样写回，不会清空存档。完成记录只保留最近 2000 笔，避免这份历史无限变大。

Listings, escrowed items, undelivered items, and unfinished payments are stored in the overworld file `data/marketmod.dat`. Upgrades keep data they know and write unknown fields back. The completion history keeps the latest 2000 records.

## 构建

需要 Java 17 或更高版本。

```
./gradlew build
```

可安装的文件在 `build/libs/marketmod-1.0.0.jar`。它不包含银行、领地或商店模组。这三个模组没有安装时，市场本身仍然能加载。
