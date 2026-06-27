# ADR-045锛氳惀閿€浠锋牸鏈嶅姟 (Price / Promotion / Coupon)

> **鐘舵€?*: 宸叉帴鍙? 
> **鍒涘缓鏃ユ湡**: 2026-06-13  
> **褰卞搷鑼冨洿**: price-service锛堣浠峰紩鎿庯級銆乸romotion-service锛堜績閿€瑙勫垯寮曟搸锛夈€乧oupon-service锛堜紭鎯犲埜锛夈€乷rder-core銆亀orkflow-service銆乧art-service銆乵ember-service
>
> **鏈枃妗ｇ郴缁熻璁¤鍗曚腑鍙扮殑璁′环寮曟搸銆佷績閿€瑙勫垯寮曟搸鍜屼紭鎯犲埜鏈嶅姟锛屾兜鐩栦紭鎯犲彔鍔犱簰鏂ヨ鍒欍€佽浠风閬撴灦鏋勩€佹姌鎵ｅ垎鎽婄畻娉曘€佷績閿€瀹氫箟銆佷紭鎯犲埜鐢熷懡鍛ㄦ湡銆佽鍒欏紩鎿庨€夊瀷浠ュ強浜嬩欢瀹氫箟銆?*

---

## 1. 鑳屾櫙

### 鐜扮姸鍒嗘瀽

褰撳墠浠锋牸/淇冮攢/浼樻儬鍒歌兘鍔涙暎钀藉湪澶氫唤 ADR 涓紝缂轰箯鐙珛缁熶竴璁捐锛?
| # | 闂 | 鐜拌薄 | 褰卞搷 |
|---|------|------|------|
| P1 | **鏃犵嫭绔?ADR** | price/promotion/coupon 浠?feature-overview.md 涓夎鎻愬強 | 鏃犳硶钀藉湴瀹炵幇 |
| P2 | **鏃犱紭鎯犲彔鍔犺鍒?* | 婊″噺 + 鎶樻墸 + 鍒歌兘鍚﹀彔鍔犳棤瀹氫箟 | 钀ラ攢娲诲姩鑰﹀悎锛岃祫鎹熼闄?|
| P3 | **Saga 姝ラ涓嶄竴鑷?* | H7: ADR-020 浠?4 姝ワ紝order-create.puml 鍚?price/coupon/promotion 鍏?7 姝?| 鏋舵瀯鐞嗚В鍋忓樊 |
| P4 | **閫€娆剧己鍒稿洖閫€** | H10: refund-flow.puml 鏃?coupon release 姝ラ | 瀹炵幇鏃堕仐婕忚祫鎹?|
| P5 | **璁′环姝ラ娣蜂贡** | M11: 澶氬璁′环姝ラ寮曠敤涓嶇粺涓€ | 瀹炵幇姝т箟 |

### 鐜版湁鑳藉姏鍒嗗竷

| 宸叉湁寮曠敤 | 浣嶇疆 | 鍐呭 |
|---------|------|------|
| promotion_calc 姝ラ | ADR-037 搂4 | YAML 妯℃澘寮曠敤 promotion-service |
| apply_coupon 姝ラ | ADR-037 搂4 | YAML 妯℃澘寮曠敤 coupon-service |
| coupon lock/release | ADR-020 搂3 | Saga 姝ラ releaseCoupon / reissueCoupon |
| 閫€娆惧埜鍥為€€ | ADR-042 搂4.3 | 宸蹭娇鐢ㄦ湭杩囨湡鍒稿洖閫€ |
| Redis L2 cache | ADR-040 | 淇冮攢鏁版嵁缂撳瓨绛栫暐 |

---

## 2. 鐩爣

| # | 鐩爣 | 琛￠噺鏍囧噯 |
|---|------|---------|
| G1 | 璁′环寮曟搸鏋舵瀯 | 鍙厤缃浠风閬擄紝鏀寔 basic鈫抦ember鈫抪romotion鈫抍oupon鈫抯hipping鈫抰ax |
| G2 | 淇冮攢瀹氫箟妯″瀷 | 缁熶竴妯″瀷瑕嗙洊婊″噺/鎶樻墸/鎷煎洟/绉掓潃 4 绉嶇被鍨?|
| G3 | 浼樻儬鍙犲姞浜掓枼瑙勫垯 | 浜岀淮浜掓枼鐭╅樀 + 浼樺厛绾у眰绾?1-10)锛屾棤閰嶇疆鍐茬獊 |
| G4 | 浼樻儬鍒哥敓鍛藉懆鏈?| Issue鈫扡ock鈫扷se鈫扲ollback 瀹屾暣鐘舵€佹満锛屽箓绛夊畨鍏?|
| G5 | Saga 闆嗘垚 | 淇 H7/H10/M11锛宲rice/coupon/promotion 绾冲叆 createOrder Saga |
| G6 | 鎶樻墸鍒嗘憡 | 澶氬晢鍝佹敮鎸佹寜閲戦姣斾緥鍒嗘憡锛屽彲閰嶇疆绛栫暐 |
| G7 | 瑙勫垯寮曟搸 | 杞婚噺鑱岃矗閾?+ SpEL锛屽畾浠?P99 100ms |

---

## 3. 鎴樻湳 DDD 璁捐

### 3.1 鑱氬悎鏍?
| 鑱氬悎鏍?| 鏁版嵁搴撹〃 | 鏍囪瘑绗?| 鐢熷懡鍛ㄦ湡 |
|--------|---------|--------|---------|
| **PromotionActivity** (淇冮攢娲诲姩) | promotion_activity | activity_id (Long, auto) | 娲诲姩鍒涘缓鈫掑彂甯冣啋鐢熸晥鈫掕繃鏈燂紝鏃堕棿椹卞姩 |
| **CouponInstance** (浼樻儬鍒稿疄渚? | coupon_instance | instance_id (Long, auto) | Issue鈫扡ock鈫扷se鈫扲ollback/Expire锛岀姸鎬佹満椹卞姩 |
| **CouponTemplate** (浼樻儬鍒告ā鏉? | coupon_template | template_id (Long, auto) | 鎸佷箙鍖栵紝涓嶅彉鏇?|

### 3.2 Entity vs Value Object

| 绫诲瀷 | 鍚嶇О | 鏍囪瘑 | 鍘熷洜 |
|------|------|------|------|
| **Entity** | PromotionActivity | activity_id | 鏈夌敓鍛藉懆鏈燂紙PENDING鈫扐CTIVE鈫扙NDED锛?|
| **Entity** | CouponInstance | instance_id | 鏈夌姸鎬佹満娴佽浆锛圓VAILABLE鈫扡OCKED鈫扷SED鈫扲EFUNDED锛?|
| **Entity** | CouponTemplate | template_id | 鎸佷箙鍖栦笉鍙橈紝浠呬綔涓虹敓鎴?Instance 鐨勫伐鍘?|
| **Value Object** | Money | amount + currency | 涓嶅彲鍙橈紝閲戦姣旇緝/璁＄畻灏佽鍦ㄥ唴閮紝鏃?ID |
| **Value Object** | DiscountAllocation | item_id + ratio + amount | 鍒嗘憡缁撴灉蹇収锛屾浛鎹㈣€岄潪淇敼 |
| **Value Object** | StackingRule | type_a + type_b + relation | 浜掓枼瑙勫垯瀹氫箟锛屾浛鎹㈠嵆鏇存柊閰嶇疆 |

### 3.3 棰嗗煙浜嬩欢

| 浜嬩欢 | 瑙﹀彂鐐?| 娑堣垂鑰?|
|------|--------|--------|
| `PromotionStarted` | 娲诲姩鐢熸晥 | cart-service (鍒锋柊璐墿杞︿环绛? |
| `PromotionEnded` | 娲诲姩杩囨湡 | cart-service (鍒锋柊璐墿杞︿环绛? |
| `CouponLocked` | 涓嬪崟閿佸埜 | order-core, workflow-service (Saga 杩借釜) |
| `CouponUsed` | 鏀粯鎴愬姛鏍搁攢 | order-core |
| `CouponRolledBack` | 璁㈠崟鍙栨秷/閫€娆炬椂鍥為€€ | order-core (鏇存柊閫€娆剧姸鎬? |
| `PriceCalculated` | 璁′环瀹屾垚 | order-core (鎺ユ敹璁′环缁撴灉) |

### 3.4 涓嶅彉鏉′欢锛圛nvariants锛?
| 瑙勫垯 | 绾︽潫 | 淇濋殰鏂瑰紡 |
|------|------|---------|
| 鍙犲姞涓嶅啿绐?| 浜掓枼鐭╅樀 + 浼樺厛绾у眰绾?| StackingRuleEngine 妫€鏌ユ墍鏈夊弬涓庝績閿€/鍒哥殑鍏崇郴 |
| 鍒嗘憡鎬诲拰 = 璁㈠崟浼樻儬鎬婚噾棰?| sum(discounts) = total_discount | 鍒嗘憡绠楁硶鎸夋瘮渚嬫垨鎸変紭鍏堢骇鍏滃簳 |
| 浼樻儬鍒镐笉閲嶅浣跨敤 | 涓€寮犲埜鍙兘鐢ㄤ簬涓€涓鍗?| coupon_instance.order_no 鍞竴绱㈠紩 |
| 鎷煎洟/绉掓潃浠蜂笉鍙備笌鍙犲姞 | 涓庝换浣曚績閿€/鍒镐簰鏂?| StackingRule 纭紪鐮佷簰鏂ュ叧绯?|

### 3.5 Repository 妯″紡

```
PromotionRepository (Interface)
  鈹溾攢鈹€ PromotionCacheRepository (Redis, 璇荤紦瀛?
  鈹斺攢鈹€ PromotionDBRepository (OB, 璇诲啓/鍐欏洖)

CouponRepository (Interface)
  鈹斺攢鈹€ CouponDBRepository (OB, 璇诲啓)
     鈹斺攢鈹€ 缂撳瓨: Redis String (coupon:instance:{id} 鈫?JSON)
```

---

## 4. 鍐崇瓥

### 鍐崇瓥 1锛氳浠峰紩鎿庢灦鏋?
| 鏂规 | 璇勪及 |
|------|------|
| **椤哄簭瑙勫垯閾?*锛堝浐瀹氶『搴?hardcode锛?| 绠€鍗曚絾涓嶅彲鎵╁睍锛屾柊瑙勫垯闇€鏀逛唬鐮?|
| **鍙厤缃閬?*锛圓pollo 椹卞姩姝ラ椤哄簭锛?| 鉁?**閫変腑** 鈥?绫讳技浜?ADR-037 YAML DSL 妯″紡锛屾瘡涓€姝ュ畾涔?order + bean锛孉pollo 鐑洿鏂?|
| **鐙珛 DSL 寮曟搸** | 鎵╁睍鎬ф渶寮轰絾瀛︿範鎴愭湰楂橈紝灏忓洟闃熶笉閫傜敤 |

**鐞嗙敱**锛氳浠锋楠わ紙basic鈫抦ember鈫抪romotion鈫抍oupon鈫抯hipping鈫抰ax锛夌浉瀵瑰浐瀹氾紝浣嗘湭鏉ュ彲鑳芥彃鍏ユ柊姝ラ锛堝 gift card / 浼佷笟浠凤級銆傚彲閰嶇疆绠￠亾閫氳繃 Apollo key `pricing.pipeline.steps` 鎺у埗姝ラ椤哄簭锛屾棤闇€鍙戝竷銆?
### 鍐崇瓥 2锛氳鍒欏紩鎿庨€夊瀷

| 鏂规 | 璇勪及 |
|------|------|
| **Drools** | 鍔熻兘寮哄ぇ浣?100MB+ 渚濊禆锛岃鍒欑紪璇戞參锛岃繍缁村鏉?|
| **杞婚噺鑱岃矗閾?+ SpEL** | 鉁?**閫変腑** 鈥?姣忎釜淇冮攢/浼樻儬鍒哥被鍨嬪搴斾竴涓?Handler锛屾潯浠惰〃杈惧紡鐢?SpEL 瑙ｆ瀽锛涚畝鍗曘€佸彲闈犮€佸彲鍗曞厓娴嬭瘯 |
| **閰嶇疆 DSL (YAML/JSON DSL)** | 鐏垫椿鎬т粙浜庝袱鑰呬箣闂达紝鏃犳爣鍑嗗簱闇€鑷缓 |

**鐞嗙敱**锛氳鍗曚腑鍙扮殑淇冮攢瑙勫垯鏁伴噺鏈夐檺锛? 50 鏉℃椿鍔ㄨ鍒欙級锛屼笉闇€瑕?Drools 绾у埆鐨勮鍒欏紩鎿庛€傝亴璐ｉ摼 + SpEL 瑕嗙洊浜嗘弧鍑忔潯浠躲€佹姌鎵ｇ巼銆侀棬妲涘垽瀹氱瓑鍏ㄩ儴鍦烘櫙銆?
### 鍐崇瓥 3锛氫績閿€瀹氫箟妯″瀷

| 鏂规 | 璇勪及 |
|------|------|
| **姣忕淇冮攢绫诲瀷鐙珛琛?* | 鎵╁睍鎬уソ浣嗘煡璇㈤渶 JOIN 澶氳〃锛屼績閿€绫诲瀷鏂板闇€ DDL |
| **缁熶竴 PromotionDefinition锛坱ype + config JSON锛?* | 鉁?**閫変腑** 鈥?type 鏋氫妇 + config JSON 瀛樻墍鏈夊弬鏁帮紝DDL 涓嶅彉锛屾柊澧炰績閿€绫诲瀷浠呭姞鏋氫妇鍊?|

**鐞嗙敱**锛氬洓绉嶄績閿€绫诲瀷锛堟弧鍑?FULL_REDUCTION / 鎶樻墸 DISCOUNT / 鎷煎洟 GROUP_BUY / 绉掓潃 FLASH锛夊叡浜浉鍚岀殑鍒涘缓/鐢熸晥/澶辨晥娴佺▼锛屼粎璁＄畻閫昏緫涓嶅悓銆傜粺涓€妯″瀷閬垮厤浜?DDL 鍙樻洿銆?
### 鍐崇瓥 4锛氫紭鎯犲彔鍔犱簰鏂ヨ鍒?
| 鏂规 | 璇勪及 |
|------|------|
| **纭紪鐮佷簰鏂ラ€昏緫** | 绠€鍗曚絾姣忔鏂板淇冮攢绫诲瀷闇€鏀逛唬鐮侊紝瀹规槗閬楁紡 |
| **浼樺厛绾у垎灞?+ 閰嶇疆鍖栦簰鏂ョ煩闃?* | 鉁?**閫変腑** 鈥?浼樺厛绾?1-10 + 浜岀淮鐭╅樀锛坧romotion_type 脳 promotion_type + promotion_type 脳 coupon_type锛夛紝Apollo 鍙厤缃?|

**璇︽儏**锛氳瑙佺 12 鑺傘€婁紭鎯犲彔鍔犱簰鏂ョ煩闃点€嬨€?
### 鍐崇瓥 5锛氭姌鎵ｅ垎鎽婄畻娉?
| 鏂规 | 璇勪及 |
|------|------|
| **鎸夐噾棰濆垎鎽?* | 鍚勫晢鍝佹寜鍗曚环閲戦鍗犳瘮鍒嗘憡鎬绘姌鎵ｏ紝閫昏緫鐩磋 |
| **鎸夋瘮渚嬪垎鎽婏紙鎸夎閲戦姣斾緥锛?* | 鉁?**閫変腑锛堥粯璁わ級** 鈥?涓庡钩鍙板璐﹂€昏緫涓€鑷达紱鍒嗘憡绮惧害寰皟鍦ㄥ垎浣嶅€硷紙0.01 鍏冿級鍏滃簳 |
| **鎸変紭鍏堢骇鍒嗘憡** | 淇冮攢鍏堝簲鐢ㄧ殑鍟嗗搧鑾峰緱鏇村鎶樻墸锛岄€傜敤浜庣壒瀹氳惀閿€鍦烘櫙 |

**鐞嗙敱**锛氭寜閲戦姣斾緥鍒嗘憡鏈€绗﹀悎鐢靛晢琛屼笟鎯緥锛岄€€鎹㈣揣鏃跺彲绮剧‘璁＄畻閫€鍥為噾棰濄€傛寜浼樺厛绾т綔涓哄彲閫夐」閰嶇疆銆?
### 鍐崇瓥 6锛氫紭鎯犲埜鐢熷懡鍛ㄦ湡

| 鏂规 | 璇勪及 |
|------|------|
| **浠呭崟琛?status 瀛楁** | status 鍙樿縼澶嶆潅锛屾棤娉曡拷婧巻鍙诧紝閫€娆惧満鏅洶闅?|
| **coupon_template + coupon_instance + 鐘舵€佹満** | 鉁?**閫変腑** 鈥?妯℃澘鍜屽疄渚嬪垎绂伙紝瀹炰緥鐘舵€佹満锛欰VAILABLE鈫扡OCKED鈫扷SED / EXPIRED / REFUNDED |

**鐞嗙敱**锛氬疄渚嬬姸鎬佹満淇濊瘉浜嗗箓绛夋€у拰鍙拷婧€с€侺OCKED锛堜笅鍗曢鍗狅級鈫?USED锛堟敮浠樻牳閿€锛夆啋 REFUNDED锛堥€€娆惧洖閫€锛変笁鎬佽浆鎹㈡竻鏅板尮閰嶈鍗曠敓鍛藉懆鏈熴€?
---

## 4. 绯荤粺鏋舵瀯

```
鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?                         API Gateway Layer                                鈹?鈹?  IGW Buyer / IGW Admin / Channel Gateway                                 鈹?鈹?  /api/v1/price/*  /api/v1/promotion/*  /api/v1/coupon/*                  鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                                    鈹?Dubbo
鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈻尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?                             price-service                                 鈹?鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹?鈹? 鈹?Layer 1: Price Calculator 璁′环寮曟搸                                    鈹? 鈹?鈹? 鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹? 鈹?鈹? 鈹? 鈹侭asicPricer   鈹?鈹侻emberPricer  鈹?鈹侾romoPricer 鈹?鈹侰ouponPricer   鈹? 鈹? 鈹?鈹? 鈹? 鈹?鍩虹浠?      鈹?鈹?浼氬憳浠?      鈹?鈹?淇冮攢鎶樻墸)  鈹?鈹?鍒镐紭鎯?       鈹? 鈹? 鈹?鈹? 鈹? 鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹? 鈹?鈹? 鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                                   鈹? 鈹?鈹? 鈹? 鈹係hippingPricer鈹?鈹俆axPricer     鈹?鈫?Apollo 鍙厤缃楠ら『搴?            鈹? 鈹?鈹? 鈹? 鈹?杩愯垂)        鈹?鈹?绋庤垂)        鈹?                                   鈹? 鈹?鈹? 鈹? 鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                                   鈹? 鈹?鈹? 鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                         鈹?Dubbo
          鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?          鈹?             鈹?             鈹?  鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈻尖攢鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈻尖攢鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈻尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹?  鈹俻romotion     鈹?鈹?coupon    鈹?鈹?member      鈹?  鈹?service      鈹?鈹?-service  鈹?鈹?-service    鈹?  鈹?             鈹?鈹?          鈹?鈹?(ADR-046)   鈹?  鈹?瑙勫垯寮曟搸     鈹?鈹?鍒哥姸鎬佹満  鈹?鈹?浼氬憳浠锋煡璇? 鈹?  鈹?淇冮攢瀹氫箟     鈹?鈹?妯℃澘/瀹炰緥 鈹?鈹?鍏嶈繍璐瑰垽瀹? 鈹?  鈹?浜掓枼鐭╅樀     鈹?鈹?鍥為€€/骞傜瓑 鈹?鈹?            鈹?  鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?          鈹?             鈹?          鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹?                 鈹?  鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈻尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?  鈹?                         Data Stores                                    鈹?  鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?  鈹?  鈹? 鈹?OB (鎸佷箙灞?  鈹? 鈹?Redis Cache L2  鈹? 鈹?Apollo (淇冮攢閰嶇疆)     鈹?  鈹?  鈹? 鈹?8 寮犺〃       鈹? 鈹?淇冮攢/鍒哥紦瀛?    鈹? 鈹?鍙犲姞瑙勫垯/绠￠亾椤哄簭    鈹?  鈹?  鈹? 鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?  鈹?  鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?```

### 缁勪欢鑱岃矗

| 缁勪欢 | 鑱岃矗 | 鎶€鏈疄鐜?|
|------|------|---------|
| **BasicPricer** | 鍩虹浠疯绠楋紙SKU 鍘熶环 / 娓犻亾浠?/ 鍖洪棿浠凤級 | 鏌?price_rule 琛紝Redis L2 |
| **MemberPricer** | 浼氬憳浠峰垽瀹氾紙璋冪敤 member-service 鑾峰彇绛夌骇鎶樻墸鐜囷級 | Dubbo 鈫?member-service |
| **PromoPricer** | 淇冮攢鎶樻墸璁＄畻锛堟弧鍑?鎶樻墸/鎷煎洟/绉掓潃锛?| 瑙勫垯寮曟搸鑱岃矗閾?|
| **CouponPricer** | 鍒镐紭鎯犺绠?+ 鍙犲姞浜掓枼鏍￠獙 | 璋冪敤 coupon-service |
| **ShippingPricer** | 杩愯垂璁＄畻锛堝惈 VIP 鍏嶈繍璐归泦鎴愶級 | 瑙勫垯寮曟搸 |
| **PromotionRuleEngine** | 淇冮攢瑙勫垯鍖归厤 + 鍙犲姞浜掓枼鍒ゅ畾 | 鑱岃矗閾?+ SpEL |
| **CouponLifecycleManager** | 鍒哥姸鎬佹満锛欼ssue鈫扡ock鈫扷se鈫扲ollback | 骞傜瓑 + RocketMQ 浜嬪姟娑堟伅 |

---

## 5. 璁′环绠￠亾娴佺▼

```
                               鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                               鈹? Apollo 閰嶇疆  鈹?                               鈹?pipeline     鈹?                               鈹?.steps 椤哄簭  鈹?                               鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹?                                      鈹?鎺у埗姝ラ椤哄簭
鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈻尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?                        璁′环绠￠亾 (Pricing Pipeline)                         鈹?鈹?                                                                          鈹?鈹? Step 1           Step 2            Step 3             Step 4             鈹?鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?   鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?   鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?   鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?       鈹?鈹?鈹?Basic    鈹傗攢鈹€鈹€鈫掆攤 Member    鈹傗攢鈹€鈹€鈫掆攤 Promotion  鈹傗攢鈹€鈹€鈫掆攤 Coupon     鈹?       鈹?鈹?鈹?Pricer   鈹?   鈹?Pricer    鈹?   鈹?Pricer     鈹?   鈹?Pricer     鈹?       鈹?鈹?鈹?(鍩虹浠? 鈹?   鈹?(浼氬憳浠?  鈹?   鈹?(淇冮攢鎶樻墸) 鈹?   鈹?(鍒镐紭鎯?   鈹?       鈹?鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?   鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?   鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?   鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?       鈹?鈹?                             鈫?                                             鈹?鈹?                    Step 5             Step 6                              鈹?鈹?                   鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?    鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                        鈹?鈹?                   鈹?Shipping  鈹傗攢鈹€鈹€鈹€鈫掆攤 Tax      鈹傗攢鈹€鈹€鈹€鈫?鏈€缁堜环              鈹?鈹?                   鈹?Pricer    鈹?    鈹?Pricer   鈹?    (final_price)        鈹?鈹?                   鈹?(杩愯垂)    鈹?    鈹?(绋庤垂)   鈹?                        鈹?鈹?                   鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?    鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                        鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?```

### 绠￠亾姝ラ璇﹁堪

| 姝ラ | 杈撳叆 | 杈撳嚭 | 璇存槑 |
|------|------|------|------|
| 1. BasicPricer | SKU_ID, quantity, channel | unit_price, line_total | SKU 鍘熶环 / 娓犻亾涓撳睘浠?/ 鍖洪棿浠?|
| 2. MemberPricer | user_id, tier_id, sku | member_discount, member_price | 璋冪敤 ADR-046 member-service 鏌ョ瓑绾ф姌鎵?|
| 3. PromoPricer | items, promotions | promo_discount, promo_detail | 瑙勫垯寮曟搸鍖归厤婊″噺/鎶樻墸/鎷煎洟/绉掓潃 |
| 3a. Stacking Check | promo_type 脳 promo_type | 鏄惁鍙彔鍔?| 浜掓枼鐭╅樀鍒ゅ畾锛屼簰鏂ュ垯鍙栨渶浼?|
| 4. CouponPricer | user_id, coupon_id, items | coupon_discount | 閿佸畾鍒?鈫?璁＄畻浼樻儬 鈫?鍙犲姞瑙勫垯鍒ゅ畾 |
| 5. ShippingPricer | items, address, member_tier | shipping_fee | 鍩虹杩愯垂 - VIP 鍏嶈繍璐癸紙浼氬憳 L3+ 鎴栭噾棰?> 闂ㄦ锛?|
| 6. TaxPricer | items, tax_region | tax_amount | 鎸夌◣鐜囪绠楃◣璐?|

---

## 6. 淇冮攢瀹氫箟妯″瀷

```sql
-- 淇冮攢瀹氫箟琛紙缁熶竴妯″瀷锛?CREATE TABLE promotion_definition (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(200) NOT NULL COMMENT '淇冮攢鍚嶇О',
    type            VARCHAR(30) NOT NULL COMMENT '淇冮攢绫诲瀷: FULL_REDUCTION/DISCOUNT/GROUP_BUY/FLASH',
    
    -- 鏃堕棿鑼冨洿
    start_time      DATETIME NOT NULL,
    end_time        DATETIME NOT NULL,
    
    -- 浣滅敤鑼冨洿
    scope_type      VARCHAR(20) NOT NULL COMMENT 'ALL/SKU/CATEGORY/BRAND',
    scope_values    JSON COMMENT '["sku_1","sku_2"] 鎴?["cat_1"]',
    
    -- 鏉′欢+浼樻儬锛圝SON 鐏垫椿鎵╁睍锛?    conditions_json JSON NOT NULL COMMENT '鏉′欢: {"min_amount":199,"min_quantity":2}',
    benefits_json   JSON NOT NULL COMMENT '浼樻儬: {"discount_rate":0.8,"reduce":50}',
    
    -- 鍙犲姞瑙勫垯
    priority        INT NOT NULL DEFAULT 5 COMMENT '浼樺厛绾?1-10, 瓒婂ぇ瓒婁紭鍏?,
    stack_group     VARCHAR(50) COMMENT '浜掓枼缁? 鍚岀粍鍐呬粎鏈€楂樹紭鍏堢骇鐢熸晥',
    
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/ACTIVE/PAUSED/EXPIRED',
    created_by      VARCHAR(50),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 淇冮攢娲诲姩瀹炰緥琛?CREATE TABLE promotion_activity (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    promotion_id    BIGINT NOT NULL,
    name            VARCHAR(200) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/ENDED',
    
    -- 娲诲姩绾у埆鐨勬潯浠惰鐩?    conditions_json JSON,
    benefits_json   JSON,
    
    -- 鍙備笌闄愬埗
    total_limit     INT COMMENT '鎬诲弬涓庢鏁伴檺鍒?,
    per_user_limit  INT DEFAULT 1 COMMENT '姣忎汉鍙備笌娆℃暟',
    used_count      INT DEFAULT 0,
    
    start_time      DATETIME NOT NULL,
    end_time        DATETIME NOT NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

---

## 7. 浼樻儬鍒告ā鍨?
```sql
-- 浼樻儬鍒告ā鏉胯〃
CREATE TABLE coupon_template (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(200) NOT NULL,
    type            VARCHAR(30) NOT NULL COMMENT 'CASH(浠ｉ噾鍒?/DISCOUNT(鎶樻墸鍒?/SHIPPING(鍏嶈繍璐?',
    
    -- 闈㈠€?    value           DECIMAL(12,2) NOT NULL COMMENT '闈㈠€奸噾棰?鎶樻墸鐜?,
    min_amount      DECIMAL(12,2) DEFAULT 0 COMMENT '鏈€浣庝娇鐢ㄩ棬妲?,
    max_discount    DECIMAL(12,2) COMMENT '鏈€澶т紭鎯犻噾棰濓紙鎶樻墸鍒哥敤锛?,
    
    -- 鍙犲姞瑙勫垯
    stack_group_id  VARCHAR(50) COMMENT '鍙犲姞缁処D, 鍚岀粍鍐呬簰鏂?,
    
    -- 鏈夋晥鏈?    validity_days   INT COMMENT '棰嗗彇鍚?N 澶╂湁鏁?,
    fixed_start     DATETIME COMMENT '鍥哄畾鏈夋晥鏈熷紑濮?,
    fixed_end       DATETIME COMMENT '鍥哄畾鏈夋晥鏈熺粨鏉?,
    
    -- 鍙戞斁鎬婚噺
    total_issued    INT DEFAULT 0,
    max_per_user    INT DEFAULT 1,
    
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED/EXPIRED',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 鐢ㄦ埛鎸佸埜瀹炰緥琛?CREATE TABLE coupon_instance (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id     BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    code            VARCHAR(64) UNIQUE COMMENT '鍒哥爜',
    
    -- 鐘舵€佹満
    status          VARCHAR(20) NOT NULL COMMENT 'AVAILABLE/LOCKED/USED/EXPIRED/REFUNDED',
    
    -- 鍏宠仈璁㈠崟
    order_no        VARCHAR(64) COMMENT '閿佸畾/浣跨敤鐨勮鍗曞彿',
    locked_at       DATETIME COMMENT '棰勫崰鏃堕棿锛堜笅鍗曪級',
    used_at         DATETIME COMMENT '鏍搁攢鏃堕棿锛堟敮浠橈級',
    refunded_at     DATETIME COMMENT '鍥為€€鏃堕棿锛堥€€娆撅級',
    
    -- 鏈夋晥鏈燂紙棰嗗彇鍚庣殑瀹為檯鏈夋晥鏈燂級
    valid_from      DATETIME NOT NULL,
    valid_to        DATETIME NOT NULL,
    
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user_status (user_id, status),
    INDEX idx_order (order_no),
    UNIQUE KEY uk_template_user (template_id, user_id, status) -- 闃叉閲嶅棰嗗彇
);
```

### 浼樻儬鍒哥姸鎬佹満

```
                    鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                    鈹?AVAILABLE 鈹? 鈫?鐢ㄦ埛棰嗗彇
                    鈹斺攢鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹?                          鈹?涓嬪崟閿佸畾
                    鈹屸攢鈹€鈹€鈹€鈹€鈻尖攢鈹€鈹€鈹€鈹€鈹?                    鈹? LOCKED   鈹? 鈫?棰勫崰闃叉浠栫敤
                    鈹斺攢鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹?                          鈹?                鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                鈹?       鈹?       鈹?          鈹屸攢鈹€鈹€鈹€鈹€鈻尖攢鈹€鈹€鈹?鈹屸攢鈻尖攢鈹€鈹€鈹€鈹?鈹屸攢鈻尖攢鈹€鈹€鈹€鈹€鈹€鈹?          鈹? USED    鈹?鈹侲XPIRED鈹?鈹俁EFUNDED鈹?          鈹?(鏀粯鏍搁攢)鈹?鈹?杩囨湡) 鈹?鈹?閫€娆惧洖閫€)鈹?          鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?```

**鐘舵€佽浆鎹㈣鍒?*锛?- `AVAILABLE 鈫?LOCKED`锛氫笅鍗曟椂棰勫崰锛屽箓绛夛紙鍚?order_no 浠呴攣涓€娆★級
- `LOCKED 鈫?USED`锛氭敮浠樻垚鍔熷洖璋冿紝鏍搁攢
- `LOCKED 鈫?AVAILABLE`锛氳鍗曞彇娑?瓒呮椂鏈敮浠橈紝瑙ｉ攣
- `USED 鈫?REFUNDED`锛氭暣鍗曢€€娆?閮ㄥ垎閫€娆炬椂鍥為€€锛堜粎鏈繃鏈熺殑鍒稿洖閫€锛屽凡杩囨湡鐨勪粎璁板綍锛?
---

## 8. 瑙勫垯寮曟搸瀹炵幇

```
鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?                     PriceCalculator                              鈹?鈹? (鍏ュ彛锛氭帴鏀惰浠疯姹傦紝閬嶅巻绠￠亾姝ラ)                                鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                           鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈻尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?                RuleChain 鑱岃矗閾?                                   鈹?鈹?                                                                  鈹?鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹?鈹? 鈹?BasicRuleChain   鈹傗啋鈫掆攤 PromotionRule    鈹傗啋鈫掆攤 CouponRule     鈹? 鈹?鈹? 鈹?                 鈹? 鈹?Chain            鈹? 鈹?Chain          鈹? 鈹?鈹? 鈹?- basePriceRule  鈹? 鈹?                 鈹? 鈹?               鈹? 鈹?鈹? 鈹?- channelPrice   鈹? 鈹?- fullReduce     鈹? 鈹?- cashRule     鈹? 鈹?鈹? 鈹?- memberPrice    鈹? 鈹?  Handler        鈹? 鈹?  Handler      鈹? 鈹?鈹? 鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹?- discount       鈹? 鈹?- discountRule 鈹? 鈹?鈹?                        鈹?  Handler        鈹? 鈹?  Handler      鈹? 鈹?鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹?- groupBuy       鈹? 鈹?- shippingRule 鈹? 鈹?鈹? 鈹?StackingRule     鈹? 鈹?  Handler        鈹? 鈹?  Handler      鈹? 鈹?鈹? 鈹?Chain            鈹傗啇鈹€鈹?- flashSale      鈹? 鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹?鈹? 鈹?                 鈹? 鈹?  Handler        鈹?                      鈹?鈹? 鈹?- mutualExclude  鈹? 鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                      鈹?鈹? 鈹?- prioritySelect 鈹?                                             鈹?鈹? 鈹?- stackValidate  鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                      鈹?鈹? 鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹?ShippingRule     鈹?                      鈹?鈹?                        鈹?Chain            鈹?                      鈹?鈹?                        鈹?                 鈹?                      鈹?鈹?                        鈹?- baseShipping   鈹?                      鈹?鈹?                        鈹?- vipFree        鈹?                      鈹?鈹?                        鈹?- regionExtra    鈹?                      鈹?鈹?                        鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                      鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?```

### 瑙勫垯 Handler 鎺ュ彛

```java
public interface PricingRuleHandler {
    /**
     * @param context 璁′环涓婁笅鏂囷紙鍟嗗搧琛屻€佺敤鎴枫€佷績閿€銆佸埜绛夛級
     * @param chain   鑱岃矗閾撅紙鐢ㄤ簬璋冪敤涓嬩竴涓?Handler锛?     * @return 璁′环缁撴灉
     */
    PricingResult handle(PricingContext context, RuleChain chain);
}

// SpEL 鏉′欢琛ㄨ揪寮忕ず渚嬶紙瀛樺偍鍦?conditions_json 涓級
// "#order.totalAmount >= 199 && #order.quantity >= 2"
// "#member.tier == 'DIAMOND' && #sku.category == 'ELECTRONICS'"
```

---

## 9. 鎶樻墸鍒嗘憡绠楁硶

### 榛樿锛氭寜閲戦姣斾緥鍒嗘憡

```
鍦烘櫙锛氳鍗曞惈 3 浠跺晢鍝侊紝鎬讳紭鎯?楼50
鍟嗗搧A: 楼100 (鍗犳瘮 50%) 鈫?鍒嗘憡 楼25
鍟嗗搧B: 楼60  (鍗犳瘮 30%) 鈫?鍒嗘憡 楼15
鍟嗗搧C: 楼40  (鍗犳瘮 20%) 鈫?鍒嗘憡 楼10
                    鍚堣:    楼50
```

**绮惧害澶勭悊**锛氬垎浣嶅€煎洓鑸嶄簲鍏ワ紝鏈€鍚庝竴鍒嗛挶鍏滃簳銆?- 鑻ュ悇鍟嗗搧鍒嗘憡鍚庡熬鏁板悎璁″鍑?楼0.02锛屼粠閲戦鏈€澶х殑鍟嗗搧鎵ｉ櫎
- 纭繚鍚勫晢鍝佸垎鎽婇噾棰濅箣鍜?= 鎬讳紭鎯犻噾棰?
### 鍙厤缃細鎸変紭鍏堢骇鍒嗘憡

```
鍦烘櫙锛氫績閿€1 婊?00-30锛堝晢鍝佺骇锛夛紝淇冮攢2 婊?00-50锛堣鍗曠骇锛?鍟嗗搧A: 楼300, 鍟嗗搧B: 楼250

瑙勫垯锛氫紭鍏堝簲鐢ㄥ埌鍟嗗搧绾т績閿€锛屽啀搴旂敤鍒拌鍗曠骇
Step 1: 淇冮攢1 鎸?A:300/B:250 姣斾緥鍒嗘憡 楼30
Step 2: 鍓╀綑 楼50 鎸?A/B 瀹為檯鏀粯閲戦姣斾緥鍒嗘憡
```

---

## 10. 浼樻儬鍙犲姞浜掓枼鐭╅樀

### 瑙勫垯鍒嗙被

| 灞傜骇 | 缁勫埆 | 鎻忚堪 | 浼樺厛绾?Level |
|------|------|------|------------|
| **鍟嗗搧绾т績閿€** | Group A | 鍗曞搧鐩撮檷銆侀檺鏃舵姌鎵?| 5 |
| **璁㈠崟绾т績閿€** | Group B | 婊″噺銆佹弧鎶?| 4 |
| **鐗规畩淇冮攢** | Group C | 鎷煎洟銆佺鏉€锛堜簰鏂ョ粍锛屼笉涓庝换浣曞叾浠栧彔鍔狅級 | 10锛堢嫭鍗狅級 |
| **浼氬憳鏉冪泭** | Group D | 浼氬憳浠凤紙涓嶄笌 Group A 鍙犲姞锛屼笉浜掓枼 Group B锛?| 6 |
| **浼樻儬鍒?* | Group E | 骞冲彴鍒搞€佸晢瀹跺埜锛堜簰鐩镐箣闂翠簰鏂ワ級 | 3 |
| **杩愯垂** | Group F | 鍏嶈繍璐广€佽繍璐规姌鎵?| 2 |

### 浜岀淮浜掓枼鐭╅樀

```
              鈹?鍗曞搧鐩撮檷 鈹?婊″噺/鎶?鈹?鎷煎洟 鈹?绉掓潃 鈹?浼氬憳浠?鈹?骞冲彴鍒?鈹?鍟嗗鍒?鈹?鍏嶈繍璐?鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹€
鍗曞搧鐩撮檷(G-A) 鈹?   -    鈹?  馃毇   鈹? 馃毇  鈹? 馃毇  鈹? 馃毇   鈹?  鉁? 鈹? 馃毇   鈹? 鉁?婊″噺/鎶?(G-B) 鈹?   馃毇   鈹?  馃毇   鈹? 馃毇  鈹? 馃毇  鈹? 鉁?  鈹?  鉁? 鈹? 馃毇   鈹? 鉁?鎷煎洟   (G-C) 鈹?   馃毇   鈹?  馃毇   鈹? 馃毇  鈹? 馃毇  鈹? 馃毇   鈹?  馃毇  鈹? 馃毇   鈹? 馃毇
绉掓潃   (G-C) 鈹?   馃毇   鈹?  馃毇   鈹? 馃毇  鈹? 馃毇  鈹? 馃毇   鈹?  馃毇  鈹? 馃毇   鈹? 馃毇
浼氬憳浠?(G-D) 鈹?   馃毇   鈹?  鉁?  鈹? 馃毇  鈹? 馃毇  鈹? 馃毇   鈹?  馃毇  鈹? 馃毇   鈹? 鉁?骞冲彴鍒?(G-E) 鈹?   鉁?  鈹?  鉁?  鈹? 馃毇  鈹? 馃毇  鈹? 馃毇   鈹?  馃毇  鈹? 馃毇   鈹? 鉁?鍟嗗鍒?(G-E) 鈹?   馃毇   鈹?  馃毇   鈹? 馃毇  鈹? 馃毇  鈹? 馃毇   鈹?  馃毇  鈹? 馃毇   鈹? 鉁?鍏嶈繍璐?(G-F) 鈹?   鉁?  鈹?  鉁?  鈹? 馃毇  鈹? 馃毇  鈹? 鉁?  鈹?  鉁? 鈹? 鉁?  鈹? -
```

**浜掓枼鍏崇郴鍥句緥**锛?- `-`锛氬悓绫诲瀷鑷姩浜掓枼锛堝悓绫诲彧鍙栨渶浼橈級
- `馃毇 浜掓枼`锛氫笉鍙彔鍔狅紝鍙栦紭鍏堢骇楂?浼樻儬閲戦澶х殑
- `鉁?鍙彔鍔燻锛氬厛搴旂敤鍟嗗搧绾э紝鍐嶅簲鐢ㄨ鍗曠骇
- `鈿狅笍 鏉′欢鍙犲姞`锛氭寜鐗瑰畾瑙勫垯锛堝鎶樻墸鍚庨噾棰濆弬涓庡埜闂ㄦ璁＄畻锛?
### 閰嶇疆鍖栧瓨鍌?
```sql
CREATE TABLE stacking_rule_config (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_a          VARCHAR(30) NOT NULL COMMENT '淇冮攢/鍒哥被鍨?A',
    type_b          VARCHAR(30) NOT NULL COMMENT '淇冮攢/鍒哥被鍨?B',
    relation        VARCHAR(20) NOT NULL COMMENT 'MUTUALLY_EXCLUSIVE/STACKABLE/CONDITIONAL',
    priority_a      INT DEFAULT 5 COMMENT '绫诲瀷 A 鐨勪紭鍏堢骇(1-10)',
    priority_b      INT DEFAULT 5,
    condition_expr  VARCHAR(500) COMMENT '鏉′欢鍙犲姞鏃剁殑 SpEL 琛ㄨ揪寮?,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_types (type_a, type_b)
);
```

---

## 11. API 璁捐

### price-service API

| 灞?| 鏂规硶 | 绔偣 | 璇存槑 |
|----|------|------|------|
| Buyer | 璁＄畻浠锋牸 | `POST /api/v1/price/calculate` | 浼犲叆鍟嗗搧琛?鐢ㄦ埛+淇冮攢锛岃繑鍥炶浠锋槑缁?|
| Buyer | 鏌ヨ杩愯垂 | `POST /api/v1/price/shipping` | 浼犲叆鍦板潃+鍟嗗搧+浼氬憳绛夌骇锛岃繑鍥炶繍璐?|
| Admin | 閰嶇疆璁′环瑙勫垯 | `POST /api/admin/v1/price/rules` | 澧炲垹鏀规煡 price_rule |
| Backend | 鏌ヨ璐圭敤鏄庣粏 | `POST /api/backend/v1/price/detail` | order-core 鏌ヨ璁′环姝ラ璇︽儏 |

### promotion-service API

| 灞?| 鏂规硶 | 绔偣 | 璇存槑 |
|----|------|------|------|
| Buyer | 鏌ヨ鍙敤淇冮攢 | `GET /api/v1/promotion/available` | 鏍规嵁 SKU/閲戦杩斿洖鍖归厤淇冮攢鍒楄〃 |
| Buyer | 璁＄畻淇冮攢浼樻儬 | `POST /api/v1/promotion/calculate` | 浼犲叆鍟嗗搧+淇冮攢 ID锛岃繑鍥炰紭鎯犻噾棰?|
| Admin | 淇冮攢 CRUD | `POST /api/admin/v1/promotion/definitions` | 澧炲垹鏀规煡 |
| Admin | 閰嶇疆鍙犲姞瑙勫垯 | `POST /api/admin/v1/promotion/stacking-rules` | 閰嶇疆浜掓枼鐭╅樀 |
| Internal | 淇冮攢鏁堟灉鏌ヨ | `GET /api/backend/v1/promotion/effect` | 缁熻/瀵硅处鐢?|

### coupon-service API

| 灞?| 鏂规硶 | 绔偣 | 璇存槑 |
|----|------|------|------|
| Buyer | 鎴戠殑浼樻儬鍒?| `GET /api/v1/coupon/list` | 绛涢€夌姸鎬?|
| Buyer | 棰嗗彇浼樻儬鍒?| `POST /api/v1/coupon/receive` | 棰嗗彇妯℃澘鐢熸垚瀹炰緥 |
| Order | 閿佸畾鍒?| `POST /api/v1/coupon/lock` | 涓嬪崟棰勫崰锛屽箓绛?|
| Order | 鏍搁攢鍒?| `POST /api/v1/coupon/use` | 鏀粯鎴愬姛鍥炶皟锛屽箓绛?|
| Order | 閲婃斁鍒?| `POST /api/v1/coupon/release` | 鍙栨秷璁㈠崟瑙ｉ攣锛屽箓绛?|
| Order | 鍥為€€鍒?| `POST /api/v1/coupon/rollback` | 閫€娆惧洖閫€锛屽箓绛?|
| Admin | 鍒告ā鏉?CRUD | `POST /api/admin/v1/coupon/templates` | 澧炲垹鏀规煡 |

---

## 12. 浜嬩欢瀹氫箟

| 浜嬩欢 | 鐢熶骇鑰?| 娑堣垂鑰?| 瑙﹀彂鏉′欢 |
|------|--------|--------|---------|
| `price.calculated` | price-service | order-core, cart-service | 璁′环瀹屾垚 |
| `promotion.effective` | promotion-service | order-core, notification | 淇冮攢鍖归厤鐢熸晥 |
| `coupon.locked` | coupon-service | order-core | 涓嬪崟閿佸埜鎴愬姛 |
| `coupon.used` | coupon-service | order-core, notification | 鏀粯鏍搁攢鎴愬姛 |
| `coupon.released` | coupon-service | order-core | 鍙栨秷璁㈠崟瑙ｉ攣 |
| `coupon.rolled_back` | coupon-service | order-core, payment-core | 閫€娆惧洖閫€ |
| `coupon.expired` | coupon-service | notification | 浼樻儬鍒歌繃鏈熸彁閱?|
| `promotion.status_changed` | promotion-service | channel-adapter, cart-service | 淇冮攢鐘舵€佸彉鏇达紙闇€鍒锋柊璐墿杞︿环绛撅級 |

---

## 13. Saga 闆嗘垚锛堜慨澶?H7/H10/M11锛?
### 淇鍚庣殑 createOrder Saga

```
Step    Service             Action                      Compensate
鈹€鈹€鈹€鈹€鈹€   鈹€鈹€鈹€鈹€鈹€鈹€鈹€             鈹€鈹€鈹€鈹€鈹€鈹€                      鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
1       order-core          createOrder                 cancelOrder
2       price-service       calculatePrice              - (浠呮煡璇㈡棤鍓綔鐢?
3       promotion-service   applyPromotion              - (浠呭尮閰嶆棤鍓綔鐢?
4       coupon-service      lockCoupon (骞傜瓑)           releaseCoupon
5       inventory-service   deductInventory (ADR-043)   undoDeduct
6       payment-core     chargePayment (ADR-042)     refund
7       order-core          confirmOrder                - (骞傜瓑缁堟€?
```

**鍙樻洿璇存槑**锛?- 鏂板 Step 2-4锛歱rice/promotion/coupon 姝ｅ紡绾冲叆 Saga 瀹氫箟 **锛堜慨澶?H7锛?*
- Step 4 coupon lock 鐨?Compensate = releaseCoupon
- 涓?ADR-020 鍏煎锛歱rice/promotion 鏄彧璇绘楠わ紙鏃犲壇浣滅敤锛夛紝涓嶉渶 Compensate

### 淇鍚庣殑 refund Saga

```
Step    Service             Action                      Notes
鈹€鈹€鈹€鈹€鈹€   鈹€鈹€鈹€鈹€鈹€鈹€鈹€             鈹€鈹€鈹€鈹€鈹€鈹€                      鈹€鈹€鈹€鈹€鈹€
1       coupon-service      rollbackCoupon (骞傜瓑)       淇 H10锛坮efund-flow 缂烘姝ラ锛?2       payment-core     refund                      ADR-042
3       inventory-service   undoDeduct                  ADR-043
4       order-core          cancelOrder                 缁堟€?```

---

## 14. 璁¤垂涓庡垎鎽?API 鏁版嵁缁撴瀯

```json
{
  "orderNo": "ORD20260613001",
  "items": [
    {
      "skuId": 1001,
      "quantity": 2,
      "unitPrice": 199.00,
      "lineTotal": 398.00,
      "discounts": [
        {"type": "MEMBER_DISCOUNT", "amount": -39.80, "detail": "閽荤煶浼氬憳浠?9鎶?},
        {"type": "FULL_REDUCTION",  "amount": -50.00, "detail": "婊?98鍑?0"}
      ],
      "shippingShare": -5.00,
      "taxShare": 15.92,
      "finalPrice": 319.12
    }
  ],
  "summary": {
    "originalTotal": 796.00,
    "memberDiscount": -79.60,
    "promotionDiscount": -100.00,
    "couponDiscount": -50.00,
    "shippingFee": 0.00,
    "taxTotal": 31.84,
    "finalTotal": 598.24
  },
  "pipelineSteps": ["BASIC", "MEMBER", "PROMOTION", "COUPON", "SHIPPING", "TAX"]
}
```

---

## 15. 瀹炴柦璁″垝

| 闃舵 | 鍐呭 | 浜哄ぉ |
|------|------|------|
| Phase 1: 璁′环寮曟搸 | BasicPricer + MemberPricer + ShippingPricer + TaxPricer + 鍙厤缃閬?| 2.5d |
| Phase 2: 淇冮攢寮曟搸 | PromotionDefinition CRUD + 瑙勫垯寮曟搸鑱岃矗閾?+ 婊″噺/鎶樻墸/鎷煎洟/绉掓潃 Handler | 2d |
| Phase 3: 浼樻儬鍙犲姞瑙勫垯 | 浜掓枼鐭╅樀閰嶇疆 + StackingRuleChain + StackingRuleConfig CRUD + Apollo 閰嶇疆 | 1d |
| Phase 4: 浼樻儬鍒哥郴缁?| CouponTemplate/Instance CRUD + 鐘舵€佹満 + Lock/Use/Release/Rollback + 骞傜瓑 | 2d |
| Phase 5: Saga 闆嗘垚 | 淇 createOrder Saga + refund Saga + 浜嬩欢鍙戝竷 | 1d |
| Phase 6: 缂撳瓨 + 鏂囨。 | Redis L2 淇冮攢缂撳瓨 + Caffeine L1 瑙勫垯缂撳瓨 + 浜ゅ弶寮曠敤鏇存柊 | 1d |

**鎬昏锛殈9.5 浜哄ぉ**

---

## 16. 浜ゅ弶寮曠敤鐭╅樀

| ADR | 鍏崇郴 | 璇存槑 |
|-----|------|------|
| **ADR-037** 搂4 | YAML 姝ラ渚濊禆 | promotion_calc / apply_coupon 姝ラ瀹氫箟 |
| **ADR-020** 搂3 | Saga 缂栨帓 | 淇 H7锛歱rice/promotion/coupon 鏂板鍒?createOrder Saga |
| **ADR-020** 搂3 | Coupon 琛ュ伩 | releaseCoupon / reissueCoupon 琛ュ伩姝ラ |
| **ADR-039** | 鐘舵€佹満 | coupon lock 鍦?checkout 鏃讹紝use 鍦?PAID 鏃?|
| **ADR-039** | 鍙栨秷璁㈠崟 | coupon release 鍦?cancelled 鏃?|
| **ADR-042** 搂4.3 | 閫€娆惧埜鍥為€€ | coupon.rollback 鍦?refund 鏃惰Е鍙?|
| **ADR-030** | 骞傜瓑妗嗘灦 | coupon lock/use/release/rollback 鍏ㄩ儴骞傜瓑 |
| **ADR-040** | 缂撳瓨绛栫暐 | Redis L2 缂撳瓨淇冮攢瀹氫箟 + Caffeine L1 瑙勫垯閰嶇疆 |
| **ADR-046** | 浼氬憳浠烽泦鎴?| MemberPricer 璋冪敤 member-service 鑾峰彇绛夌骇鎶樻墸 |
| **ADR-044** | 璐墿杞﹂泦鎴?| 璐墿杞﹁皟鐢?price-service 鍒锋柊浠风 |
| **ADR-047** | 椋庢帶闆嗘垚 | 閫€娆炬椂椋庢帶璇勫垎褰卞搷 coupon rollback 鍐崇瓥 |
| **ADR-038** | API 瑙勮寖 | ApiResult\<T\> 鏍囧噯杩斿洖 |
| **ADR-038** 搂3 | 浜嬩欢涓績 | 鎵€鏈変簨浠惰蛋 event center |

---

## 17. 椋庨櫓鐭╅樀

| 椋庨櫓 | 姒傜巼 | 褰卞搷 | 缂撹В鎺柦 |
|------|------|------|---------|
| 鍙犲姞浜掓枼瑙勫垯閰嶇疆鍐茬獊 | 涓?| 楂橈紙璧勬崯锛?| 鏍￠獙閲嶅瑙勫垯锛岀姝㈢煕鐩鹃厤缃紱Apollo 鍙樻洿閫氱煡瀹℃壒 |
| 浼樻儬鍒搁噸澶嶆牳閿€ | 浣?| 楂橈紙璧勬崯锛?| 骞傜瓑妗嗘灦锛坮equest_id锛? CF 鐘舵€佹満 atomic update |
| 璁′环绠￠亾姝诲惊鐜?| 浣?| 涓?| 绠￠亾姝ラ鏁颁笂闄?20 姝?+ 瓒呮椂 5s |
| 鎶樻墸鍒嗘憡绮惧害涓㈠け | 涓?| 浣?| 鍒嗕綅鍊煎厹搴?+ 鏈€鍚庝竴鍒嗛挶浠庢渶澶ч噾棰濇墸闄?|
| 淇冮攢鏁版嵁缂撳瓨涓嶄竴鑷?| 涓?| 涓?| 閰嶇疆浜?Active/Updated 鏃堕棿 + 5min 鏈€澶?TTL |
