# ADR-036锛氬叏娓犻亾璁㈠崟鎺ュ叆涓庡垎鍙?
## 鐘舵€?
宸叉帴鍙?
---

## 鑳屾櫙

### 鐜扮姸鍒嗘瀽

褰撳墠璁㈠崟涓彴鐨勮璁¤捣鐐规槸**鍐呴儴鍓嶇鐩存帴璋冪敤 API**锛堜拱瀹?APP/鍟嗗鍚庡彴/杩愯惀鍚庡彴锛夛紝閫氳繃 IGW Buyer + IGW Admin + Ext Gateway 涓夊疄渚嬪鐞嗗唴閮ㄤ笌寮€鏀惧钩鍙扮殑娴侀噺銆?*鏁翠釜鏋舵瀯娌℃湁璁捐澶栭儴閿€鍞笭閬撶殑鎺ュ叆鑳藉姏銆?*

鍏蜂綋缂哄け锛?
**闂 1锛氭棤澶栭儴娓犻亾鎺ュ叆灞?*  
鎵€鏈?API 鍏ュ彛鍋囪璇锋眰鏉ヨ嚜鍙椾俊浠荤殑鍓嶇搴旂敤銆傚鏋滃ぉ鐚€佷含涓溿€佹嫾澶氬銆佹姈闊崇瓑娓犻亾闇€瑕佸皢璁㈠崟鎺ㄩ€佸埌涓彴锛屾垨涓彴闇€瑕佷粠娓犻亾鎷夊彇璁㈠崟锛屽綋鍓嶆病鏈変换浣曢€傞厤灞傛潵澶勭悊娓犻亾鐗规湁鐨勫崗璁€佽璇佸拰鏁版嵁鏍煎紡銆?
**闂 2锛氭棤璁㈠崟鏍囧噯鍖栬浆鎹?*  
姣忎釜澶栭儴娓犻亾鏈夎嚜宸辩嫭绔嬬殑璁㈠崟鏁版嵁缁撴瀯锛堝瓧娈靛悕銆佸祵濂楃粨鏋勩€佹灇涓惧€硷級銆傚綋鍓嶆灦鏋勬病鏈変粠娓犻亾鐗规湁鏍煎紡鍒扮粺涓€璁㈠崟妯″瀷鐨勬爣鍑嗗寲鏄犲皠寮曟搸銆?
**闂 3锛氭棤娓犻亾璺敱鏈哄埗**  
涓嶅悓娓犻亾鐨勮鍗曢渶瑕佽矾鐢卞埌涓嶅悓涓氬姟绾匡紙鐢靛晢/鏈湴鐢熸椿/B2B锛夊拰涓嶅悓澶勭悊娴佺▼銆傚綋鍓嶆灦鏋勭己灏戝彲閰嶇疆鐨勬笭閬撹矾鐢辫鍒欍€?
**闂 4锛氭棤娓犻亾鐘舵€佸悓姝?*  
璁㈠崟鐘舵€佸彉鏇村悗锛堟敮浠樻垚鍔?鍙戣揣/瀹屾垚/閫€娆撅級锛岄渶瑕佸悓姝ュ洖澶栭儴娓犻亾銆傚綋鍓嶆灦鏋勪粎鏈夊唴閮ㄧ姸鎬佹祦杞紝缂哄皯鍚戞笭閬撴帹閫佺姸鎬佸拰鐗╂祦淇℃伅鐨勮兘鍔涖€?
### 鐩爣

1. **鍏ㄦ笭閬撴帴鍏?*锛氭敮鎸佸ぉ鐚€佷含涓溿€佹嫾澶氬銆佹姈闊炽€佸井淇″皬绋嬪簭銆佺嚎涓?POS 绛変富娴侀攢鍞笭閬撶殑璁㈠崟鎺ュ叆
2. **鏍囧噯鍖栬浆鎹?*锛氬皢娓犻亾鐗规湁璁㈠崟鏍煎紡缁熶竴杞崲涓轰腑鍙版爣鍑嗚鍗曟ā鍨?3. **鍙厤缃矾鐢?*锛氭寜娓犻亾绫诲瀷 + 鍟嗗搧绫荤洰鍔ㄦ€佽矾鐢卞埌瀵瑰簲涓氬姟绾垮拰澶勭悊娴佺▼
4. **鍙屽悜鍚屾**锛氳鍗曠姸鎬?/ 鐗╂祦淇℃伅 / 搴撳瓨淇℃伅鍙悓姝ュ洖澶栭儴娓犻亾
5. **鍙墿灞曟€?*锛氭柊澧炴笭閬撻€氳繃 SPI 鎵╁睍锛屼笉淇敼鏍稿績浠ｇ爜

---

## 鍐崇瓥

### 鍐崇瓥 1锛氭笭閬撴帴鍏ユā寮?鈥?娣峰悎妯″紡锛圥ush + Pull锛?
| 绛栫暐 | 璇存槑 | 璇勪及 |
|------|------|------|
| **绾?Push** | 娓犻亾閫氳繃 Webhook 灏嗚鍗曟帹閫佸埌涓彴 | 瀹炴椂鎬уソ锛屼絾閮ㄥ垎娓犻亾涓嶆敮鎸?Push 鎴栨湁鍙潬鎬ч棶棰?|
| **绾?Pull** | 涓彴瀹氭椂鎷夊彇娓犻亾璁㈠崟 | 瀹炵幇缁熶竴锛屼絾寤惰繜楂樸€佹笭閬?API 闄愭祦 |
| **娣峰悎妯″紡** | Push 涓轰富锛堝疄鏃惰鍗曪級+ Pull 涓鸿ˉ鍏咃紙瀹氭椂瀵硅处銆佽ˉ婕忥級 | 鉁?**閫変腑** |

**鐞嗙敱**锛? 
- 涓绘祦鐢靛晢骞冲彴锛堝ぉ鐚?浜笢/鎷煎澶氾級鏀寔娑堟伅鎺ㄩ€佹垨鍥炶皟閫氱煡锛屽疄鏃舵€т紭浜?Pull
- 鎵€鏈夊钩鍙伴兘鍙兘鍑虹幇娑堟伅涓㈠け鎴栭噸澶嶏紝Pull 瀹氭椂瀵硅处浣滀负琛ュ伩鏈哄埗
- Push 澶勭悊瀹炴椂璁㈠崟锛堢绾у欢杩燂級锛孭ull 澶勭悊琛ユ紡鍜屾棩缁堝璐︼紙灏忔椂绾у欢杩燂級

### 鍐崇瓥 2锛氭笭閬撻€傞厤鏋舵瀯 鈥?缁熶竴閫傞厤妗嗘灦 + SPI 鎻掍欢

| 绛栫暐 | 璇勪及 |
|------|------|
| 姣忎釜娓犻亾涓€涓嫭绔嬪井鏈嶅姟 | 閮ㄧ讲鎴愭湰楂橈紝鍏叡閫昏緫閲嶅锛屾柊澧炴笭閬撴參 |
| **缁熶竴閫傞厤妗嗘灦 + SPI 鎻掍欢** | 鉁?**閫変腑** 鈥?鍏变韩鍩虹璁炬柦锛屾彃浠堕殧绂绘笭閬撳樊寮?|

**鐞嗙敱**锛? 
- 娓犻亾閫傞厤鐨勫叕鍏遍€昏緫锛堣璇併€侀檺娴併€侀噸璇曘€佺洃鎺э級缁熶竴鍦ㄦ鏋跺眰瀹炵幇
- 娓犻亾宸紓閫昏緫锛堢鍚嶇畻娉曘€佸瓧娈垫槧灏勩€佺姸鎬佹灇涓炬槧灏勶級閫氳繃 SPI 鎻掍欢闅旂
- 鏂板娓犻亾 = 鏂板涓€涓?SPI 瀹炵幇 + Apollo 閰嶇疆锛屾棤闇€鏂板缓鏈嶅姟鎴栭噸鍚?
### 鍐崇瓥 3锛氳鍗曟爣鍑嗗寲绛栫暐 鈥?杈逛晶鏍囧噯鍖?+ 鍙屽瓨鍌?
| 绛栫暐 | 璇勪及 |
|------|------|
| 杈逛晶鏍囧噯鍖栵紙Adapter 灞傚畬鎴愯浆鎹紝瀛樺叆缁熶竴 `order` 琛級 | 鏌ヨ鎬ц兘濂斤紝浣嗕涪澶卞師濮嬫暟鎹?|
| **杈逛晶鏍囧噯鍖?+ 鍘熷鏁版嵁瀛樻。** | 鉁?**閫変腑** 鈥?鏃㈡湁鏍囧噯鍖栨煡璇㈣兘鍔涳紝鍙堟湁鍘熷鏁版嵁鐢ㄤ簬瀵硅处鍜岄棶棰樻帓鏌?|
| 鍏ㄩ噺瀛樺偍鍘熷鏁版嵁锛屾煡璇㈡椂鍔ㄦ€佽浆鎹?| 鏌ヨ鎬ц兘宸紝杞崲閫昏緫澶嶆潅 |

**鐞嗙敱**锛? 
- `channel_raw_order` 琛ㄤ繚瀛樻笭閬撳師濮嬭鍗?JSON锛堝彲杩芥函銆佸彲瀵硅处锛?- `order` 琛ㄤ繚瀛樻爣鍑嗗寲鍚庣殑缁熶竴璁㈠崟妯″瀷锛堥珮鏁堟煡璇€佺粺涓€澶勭悊锛?- 瀵硅处鏃堕€氳繃 `channel_order_no` 鍏宠仈涓ゅ紶琛?
### 鍐崇瓥 4锛氱姸鎬佸悓姝ユā寮?鈥?寮傛浜嬩欢椹卞姩

| 绛栫暐 | 璇勪及 |
|------|------|
| 鍚屾 RPC 鎺ㄩ€侊紙璁㈠崟鍙樻洿鏃跺悓姝?Push 鍒版笭閬擄級 | 璋冪敤澶辫触闃诲涓绘祦绋嬶紝娓犻亾瓒呮椂褰卞搷鏍稿績閾捐矾 |
| **寮傛 MQ 浜嬩欢椹卞姩** | 鉁?**閫変腑** 鈥?涓绘祦绋嬫棤闃诲锛屽彲闈犳姇閫掞紝鍙噸璇?|
| 瀹氭椂鎵归噺鍚屾 | 寤惰繜楂橈紝涓嶉€傚悎瀹炴椂鍦烘櫙 |

**鐞嗙敱**锛? 
- 璁㈠崟鏍稿績閾捐矾涓嶄緷璧栨笭閬撳悓姝ョ殑鎴愬姛涓庡惁
- 娓犻亾鍚屾澶辫触閫氳繃 MQ 閲嶈瘯 + XXL-Job 鍏滃簳
- 閬靛惊鐜版湁浜嬩欢椹卞姩鏋舵瀯锛圤rderPaidEvent銆丱rderShippedEvent 绛夛級

### 鍐崇瓥 5锛氭笭閬撻厤缃鐞?鈥?Apollo 閰嶇疆椹卞姩

| 绛栫暐 | 璇勪及 |
|------|------|
| DB 瀛樺偍娓犻亾閰嶇疆 | 閰嶇疆鍙樻洿闇€閲嶅惎鎴栧鍔犲埛鏂伴€昏緫 |
| **Apollo 閰嶇疆椹卞姩** | 鉁?**閫変腑** 鈥?鐑敓鏁堬紝鐜版湁鍩虹璁炬柦锛岀伆搴﹀彂甯?|

**鐞嗙敱**锛? 
- Apollo 宸叉槸涓彴缁熶竴閰嶇疆涓績锛屾棤闇€寮曞叆鏂扮郴缁?- 娓犻亾寮€鍏炽€佽矾鐢辫鍒欍€侀檺娴侀槇鍊兼敮鎸佺儹鏇存柊
- 鏀寔鐏板害鍙戝竷鏂版笭閬擄紙鍏堢伆搴?10% 娴侀噺锛?
### 鍐崇瓥 6锛欳hannel Gateway 閮ㄧ讲浣嶇疆 鈥?鐙珛 Gateway 瀹炰緥

| 绛栫暐 | 璇勪及 |
|------|------|
| 璺敱鍒?IGW Buyer | 娓犻亾娴侀噺涓庝拱瀹舵祦閲忔贩鍚堬紝闄愭祦/閴存潈绛栫暐鍐茬獊 |
| 璺敱鍒?External Gateway | 娓犻亾 App 璁よ瘉涓庣涓夋柟寮€鍙戣€呰璇佹贩鍦ㄤ竴璧?|
| **鐙珛 Channel Gateway 瀹炰緥** | 鉁?**閫変腑** 鈥?娴侀噺闅旂锛岀嫭绔嬫墿缂╁锛屾笭閬撶壒鏈夌殑閴存潈鍜岄檺娴佺瓥鐣?|

**鐞嗙敱**锛? 
- 娓犻亾娴侀噺妯″瀷涓庡唴閮ㄥ墠绔殑鍖哄埆锛氭笭閬撹姹傞€氬父鏉ヨ嚜鍥哄畾 IP銆佷娇鐢?HMAC 鎴?OAuth 璁よ瘉銆丵PS 妯″瀷绋冲畾浣嗘暟鎹噺澶?- 娓犻亾 Gateway 鏁呴殰涓嶅奖鍝嶄拱瀹?鍟嗗/杩愯惀娴侀噺
- 鐙珛 Channel Gateway 鍙互閮ㄧ讲鍦ㄧ澶栭儴缃戠粶鏇磋繎鐨勪綅缃紙濡?DMZ 鍖猴級

---

## 璇︾粏璁捐

### 1. 鏋舵瀯鎬昏

```
澶栭儴娓犻亾锛圥ush 妯″紡锛?                       澶栭儴娓犻亾锛圥ull 妯″紡锛?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?        鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?澶╃尗      鈹?鈹?浜笢      鈹?鈹?鎶栭煶      鈹?        鈹?鎷煎澶?   鈹?鈹?POS 闂ㄥ簵 鈹?鈹?Webhook   鈹?鈹?Webhook   鈹?鈹?Webhook   鈹?        鈹?API      鈹?鈹?API      鈹?鈹斺攢鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹?        鈹斺攢鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹?     鈹?           鈹?            鈹?                    鈹?          鈹?     鈻?           鈻?            鈻?                    鈹?          鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?         鈹?          鈹?鈹?         Channel Gateway                  鈹?         鈹?          鈹?鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?         鈹?          鈹?鈹? 鈹? AuthFilter锛堟笭閬撹璇侊級               鈹?鈹?         鈹?          鈹?鈹? 鈹? RateLimitFilter锛堟笭閬撶骇闄愭祦锛?        鈹?鈹?         鈹?          鈹?鈹? 鈹? ChannelRouteFilter锛堟寜娓犻亾璺敱锛?     鈹?鈹?         鈹?          鈹?鈹? 鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?         鈹?          鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?         鈹?          鈹?                    鈹?                                 鈹?          鈹?                    鈻?                                 鈻?          鈻?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?                     Channel Adapter 灞?                           鈹?鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹愨攤
鈹? 鈹傚ぉ鐚獳dapter 鈹?鈹備含涓淎dapter鈹?鈹傛姈闊矨dapter鈹?鈹傛嫾澶氬    鈹?鈹?POS      鈹傗攤
鈹? 鈹係PI 瀹炵幇    鈹?鈹係PI 瀹炵幇   鈹?鈹係PI 瀹炵幇   鈹?鈹侾ull Job  鈹?鈹侾ull Job  鈹傗攤
鈹? 鈹斺攢鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹樷攤
鈹?      鈹?           鈹?           鈹?           鈹?           鈹?      鈹?鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?   鈹?鈹? 鈹? 璁㈠崟鏍囧噯鍖栧紩鎿庯紙Normalization Engine锛?                    鈹?   鈹?鈹? 鈹? 瀛楁鏄犲皠 + 鏁版嵁琛ュ叏 + 瑙勫垯鏍￠獙 + 涓氬姟绾胯矾鐢?                鈹?   鈹?鈹? 鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?   鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                                      鈹?                                      鈻?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹? Channel Registry锛圓pollo 娓犻亾娉ㄥ唽琛級                               鈹?鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?鈹? 鈹?channel_cfg 鈹?鈹?route_rule  鈹?鈹?field_mapping鈹?鈹?status_mapping   鈹?鈹?鈹? 鈹?娓犻亾閰嶇疆    鈹?鈹?璺敱瑙勫垯    鈹?鈹?瀛楁鏄犲皠    鈹?鈹?鐘舵€佹槧灏?         鈹?鈹?鈹? 鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                               鈹?                               鈻?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?                      璁㈠崟涓彴鏍稿績鏈嶅姟                                鈹?鈹? order-core  workflow-service  payment-core  inventory-service  鈹?鈹? logistics-service  aftersale-service                               鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                               鈹?                               鈻?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?            娓犻亾鐘舵€佸悓姝ュ眰锛圓sync via MQ + XXL-Job锛?                鈹?鈹? 鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹愨攤
鈹? 鈹? OrderEvent 鈫?RocketMQ 鈫?ChannelStatusSync Consumer            鈹傗攤
鈹? 鈹?   鈫?Adapter.syncStatus(channelOrderNo, status)                鈹傗攤
鈹? 鈹? 鈫?閲嶈瘯闃熷垪 鈫?DLQ 鈫?XXL-Job 姣忔棩瀵硅处琛ユ紡                       鈹傗攤
鈹? 鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹樷攤
鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?```

### 2. Channel Gateway 璁捐

#### 2.1 璺敱涓庤璇?
```yaml
# channel-gateway.yml 鈥?娓犻亾 Gateway 閰嶇疆
spring:
  cloud:
    gateway:
      routes:
        # ===== 娓犻亾 Webhook 鎺ユ敹绔偣 =====
        - id: channel-tmall-webhook
          uri: lb://channel-adapter
          predicates:
            - Path=/channel/tmall/webhook/**
          filters:
            - name: ChannelAuth
              args:
                channelType: tmall
            - name: RequestRateLimiter
              args:
                key-resolver: '#{@channelKeyResolver}'
                redis-rate-limiter.replenishRate: 500
                redis-rate-limiter.burstCapacity: 1000

        - id: channel-jd-webhook
          uri: lb://channel-adapter
          predicates:
            - Path=/channel/jd/webhook/**
          filters:
            - name: ChannelAuth
              args:
                channelType: jd
            - name: RequestRateLimiter
              args:
                key-resolver: '#{@channelKeyResolver}'
                redis-rate-limiter.replenishRate: 500
                redis-rate-limiter.burstCapacity: 1000

        - id: channel-douyin-webhook
          uri: lb://channel-adapter
          predicates:
            - Path=/channel/douyin/webhook/**
          filters:
            - name: ChannelAuth
              args:
                channelType: douyin
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 300
                redis-rate-limiter.burstCapacity: 600

        # ===== 娓犻亾绠＄悊 API =====
        - id: channel-admin
          uri: lb://channel-adapter
          predicates:
            - Path=/admin/channels/**
          filters:
            - AddRequestHeader=X-Call-Source, admin
```

#### 2.2 娓犻亾璁よ瘉绛栫暐

涓嶅悓娓犻亾浣跨敤涓嶅悓鐨勮璇佹柟寮忥細

| 娓犻亾 | 璁よ瘉鏂瑰紡 | 璇存槑 |
|------|---------|------|
| 澶╃尗 | AppKey + Signature(HMAC-SHA256) | 澶╃尗寮€鏀惧钩鍙版爣鍑嗙鍚?|
| 浜笢 | AppKey + Secret + Timestamp | 浜笢瀹欐柉骞冲彴绛惧悕 |
| 鎷煎澶?| PopAuth + AccessToken | OAuth2.0 access_token |
| 鎶栭煶 | OAuth2.0 Client Credentials | 搴旂敤绾ф巿鏉?|
| 灏忕▼搴?| SessionKey + 娑堟伅浣撶鍚?| 寰俊娑堟伅鍔犺В瀵?|
| POS 闂ㄥ簵 | API Key + IP 鐧藉悕鍗?| 鍥哄畾瀵嗛挜 + 鏉ユ簮 IP |

```java
/**
 * 娓犻亾璁よ瘉 SPI
 * 姣忎釜娓犻亾瀹炵幇鑷繁鐨勮璇侀€昏緫
 */
public interface ChannelAuthProvider {

    String getChannelType();

    /**
     * 璁よ瘉娓犻亾璇锋眰
     * @param exchange Gateway 璇锋眰涓婁笅鏂?     * @return 璁よ瘉缁撴灉锛堥€氳繃/鎷掔粷 + 娓犻亾鐢ㄦ埛/搴楅摵鏍囪瘑锛?     */
    Mono<ChannelAuthResult> authenticate(ServerWebExchange exchange);

    /**
     * 鍒锋柊娓犻亾鍑瘉锛堝 access_token 杩囨湡鏃讹級
     */
    Mono<Void> refreshCredentials(String channelShopId);
}

/**
 * 娓犻亾璁よ瘉 Filter
 */
@Component
public class ChannelAuthFilter implements GlobalFilter {

    private final Map<String, ChannelAuthProvider> authProviderMap;

    public ChannelAuthFilter(List<ChannelAuthProvider> providers) {
        this.authProviderMap = providers.stream()
                .collect(Collectors.toMap(ChannelAuthProvider::getChannelType, Function.identity()));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String channelType = extractChannelType(path); // /channel/{channelType}/...

        ChannelAuthProvider provider = authProviderMap.get(channelType);
        if (provider == null) {
            return unauthorized(exchange, "Unsupported channel: " + channelType);
        }

        return provider.authenticate(exchange)
                .flatMap(result -> {
                    if (!result.isAuthenticated()) {
                        return unauthorized(exchange, result.getReason());
                    }
                    exchange.getAttributes().put("channel_type", channelType);
                    exchange.getAttributes().put("channel_shop_id", result.getShopId());
                    return chain.filter(exchange);
                });
    }
}
```

### 3. Channel Adapter SPI

#### 3.1 SPI 鎺ュ彛瀹氫箟

```java
/**
 * 娓犻亾閫傞厤鍣?SPI
 * 姣忎釜閿€鍞笭閬撳疄鐜版鎺ュ彛鏉ュ鐞嗙壒鏈夌殑鍗忚鍜屾暟鎹牸寮? */
public interface ChannelAdapter {

    /**
     * 杩斿洖娓犻亾绫诲瀷鏍囪瘑
     */
    String getChannelType();

    /**
     * 瑙ｆ瀽娓犻亾鍘熷璁㈠崟 鈫?鏍囧噯鍖栬鍗?     * @param rawRequest 娓犻亾鎺ㄩ€?鎷夊彇鐨勫師濮嬭姹傛暟鎹?     * @param context    娓犻亾涓婁笅鏂囷紙娓犻亾搴楅摵 ID銆佽姹傛潵婧愮瓑锛?     * @return 鏍囧噯鍖栧悗鐨勮鍗曟暟鎹?     */
    NormalizedOrder normalize(RawChannelRequest rawRequest, ChannelContext context);

    /**
     * 璁㈠崟鐘舵€佸洖鎺ㄥ埌娓犻亾
     * @param channelOrderNo 娓犻亾渚ц鍗曞彿
     * @param status         涓彴鍐呴儴鐘舵€?     * @param extra          闄勫姞鏁版嵁锛堢墿娴佸崟鍙枫€侀€€娆鹃噾棰濈瓑锛?     * @return 娓犻亾鍚屾缁撴灉
     */
    ChannelSyncResult syncStatus(String channelOrderNo, OrderStatus status, Map<String, Object> extra);

    /**
     * 鎷夊彇娓犻亾璁㈠崟锛圥ull 妯″紡锛?     * @param shopId    娓犻亾搴楅摵 ID
     * @param startTime 鎷夊彇璧峰鏃堕棿
     * @param endTime   鎷夊彇鎴鏃堕棿
     * @return 鍘熷璁㈠崟鍒楄〃
     */
    List<RawChannelRequest> pullOrders(String shopId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 妫€鏌ユ笭閬撳嚟璇佹槸鍚︽湁鏁堬紙Pull 妯″紡浣跨敤锛?     */
    boolean validateCredential(String shopId);
}
```

#### 3.2 鍐呯疆閫傞厤鍣ㄦ敞鍐?
```java
/**
 * 閫傞厤鍣ㄦ敞鍐屼腑蹇? * SPI + Spring 鑷姩娉ㄥ叆
 */
@Component
public class ChannelAdapterRegistry {

    private final Map<String, ChannelAdapter> adapterMap;
    private final Map<String, ChannelAuthProvider> authProviderMap;

    public ChannelAdapterRegistry(List<ChannelAdapter> adapters,
                                   List<ChannelAuthProvider> authProviders) {
        this.adapterMap = adapters.stream()
                .collect(Collectors.toMap(ChannelAdapter::getChannelType, Function.identity()));
        this.authProviderMap = authProviders.stream()
                .collect(Collectors.toMap(ChannelAuthProvider::getChannelType, Function.identity()));
    }

    public ChannelAdapter getAdapter(String channelType) {
        ChannelAdapter adapter = adapterMap.get(channelType);
        if (adapter == null) {
            throw new UnsupportedChannelException("Unsupported channel: " + channelType);
        }
        return adapter;
    }

    public Set<String> getSupportedChannels() {
        return adapterMap.keySet();
    }
}
```

#### 3.3 绀轰緥锛氬ぉ鐚€傞厤鍣?
```java
/**
 * 澶╃尗娓犻亾閫傞厤鍣? * @ChannelType("tmall")
 */
@Component
public class TmallChannelAdapter implements ChannelAdapter {

    @Override
    public String getChannelType() { return "tmall"; }

    @Override
    public NormalizedOrder normalize(RawChannelRequest rawRequest, ChannelContext context) {
        TmallOrderDTO tmallOrder = rawRequest.parseBody(TmallOrderDTO.class);

        return NormalizedOrder.builder()
                .channelType("tmall")
                .channelOrderNo(tmallOrder.getTradeId())
                .channelShopId(context.getShopId())
                .buyerInfo(NormalizedBuyer.builder()
                        .channelBuyerId(tmallOrder.getBuyerNick())
                        .receiverName(tmallOrder.getReceiverName())
                        .receiverPhone(tmallOrder.getReceiverPhone())
                        .receiverAddress(tmallOrder.getFullAddress())
                        .build())
                .orderItems(tmallOrder.getOrderItems().stream()
                        .map(item -> NormalizedOrderItem.builder()
                                .skuId(item.getSkuId())
                                .productName(item.getTitle())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getPrice().longValue())
                                .totalPrice(item.getTotalFee().longValue())
                                .build())
                        .collect(Collectors.toList()))
                .totalAmount(tmallOrder.getTotalFee().longValue())
                .freightAmount(tmallOrder.getPostFee().longValue())
                .discountAmount(tmallOrder.getDiscountFee().longValue())
                .payAmount(tmallOrder.getPayment().longValue())
                .status(mapOrderStatus(tmallOrder.getTradeStatus()))
                .orderTime(tmallOrder.getGmtCreate())
                .rawJson(rawRequest.getRawBody())      // 淇濆瓨鍘熷鏁版嵁
                .build();
    }

    @Override
    public ChannelSyncResult syncStatus(String channelOrderNo, OrderStatus status, Map<String, Object> extra) {
        TmallStatusSyncRequest syncRequest = TmallStatusSyncRequest.builder()
                .tradeId(channelOrderNo)
                .status(mapToTmallStatus(status))
                .logisticsNo((String) extra.get("logisticsNo"))
                .logisticsCompany((String) extra.get("logisticsCompany"))
                .build();

        // 璋冪敤澶╃尗 API 鍥炲啓鐘舵€?        return tmallClient.syncOrderStatus(syncRequest);
    }

    private OrderStatus mapOrderStatus(String tmallStatus) {
        // 澶╃尗鐘舵€?鈫?涓彴鐘舵€佹槧灏?        switch (tmallStatus) {
            case "WAIT_BUYER_PAY":      return OrderStatus.PENDING_PAY;
            case "WAIT_SELLER_SEND_GOODS": return OrderStatus.PAID;
            case "WAIT_BUYER_CONFIRM":   return OrderStatus.SHIPPED;
            case "TRADE_FINISHED":       return OrderStatus.DELIVERED;
            case "TRADE_CLOSED":         return OrderStatus.CANCELLED;
            case "TRADE_CLOSED_BY_TAOBAO": return OrderStatus.CLOSED;
            default: throw new UnknownStatusException("Unknown tmall status: " + tmallStatus);
        }
    }

    private String mapToTmallStatus(OrderStatus status) {
        // 涓彴鐘舵€?鈫?澶╃尗鐘舵€佹槧灏?        switch (status) {
            case SHIPPED:    return "WAIT_BUYER_CONFIRM";
            case DELIVERED:  return "TRADE_FINISHED";
            case CANCELLED:  return "TRADE_CLOSED";
            default: return null; // 澶╃尗涓嶅叧蹇冨叾浠栫姸鎬?        }
    }
}
```

#### 3.4 Channel Adapter 鏈嶅姟

```java
/**
 * 娓犻亾閫傞厤鏈嶅姟 鈥?璁㈠崟鎺ュ叆鍏ュ彛
 */
@RestController
@RequestMapping("/channel/{channelType}/webhook")
public class ChannelIngestionController {

    private final ChannelAdapterRegistry adapterRegistry;
    private final OrderNormalizationEngine normalizationEngine;
    private final OrderCreationService orderCreationService;

    @PostMapping("/order")
    public ResponseEntity<Void> receiveOrder(
            @PathVariable String channelType,
            @RequestBody String rawBody,
            @RequestHeader HttpHeaders headers) {

        // 1. 鑾峰彇娓犻亾閫傞厤鍣?        ChannelAdapter adapter = adapterRegistry.getAdapter(channelType);

        // 2. 鏋勫缓娓犻亾涓婁笅鏂?        ChannelContext context = ChannelContext.builder()
                .channelType(channelType)
                .shopId(headers.getFirst("X-Shop-Id"))
                .requestId(headers.getFirst("X-Request-Id"))
                .build();

        // 3. 鏍囧噯鍖栬鍗?        RawChannelRequest rawRequest = new RawChannelRequest(rawBody, headers);
        NormalizedOrder normalized = adapter.normalize(rawRequest, context);

        // 4. 鎵ц鏍囧噯鍖栧紩鎿庯紙琛ュ叏 + 鏍￠獙 + 涓氬姟绾胯矾鐢憋級
        NormalizedOrder enriched = normalizationEngine.enrich(normalized);

        // 5. 鍒涘缓璁㈠崟锛堣皟鐢?order-core锛?        orderCreationService.createChannelOrder(enriched);

        return ResponseEntity.accepted().build(); // 寮傛澶勭悊
    }
}
```

### 4. 璁㈠崟鏍囧噯鍖栧紩鎿?
#### 4.1 鏍囧噯鍖栨祦绋?
```
鍘熷娓犻亾璁㈠崟
     鈹?     鈻?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?Step 1: 瀛楁鏄犲皠              鈹?鈹? 娓犻亾鐗规湁瀛楁 鈫?鏍囧噯瀛楁        鈹?鈹? SPI 瀹炵幇锛堟瘡涓笭閬撳悇鑷疄鐜帮級   鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?     鈹?     鈻?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?Step 2: 鏁版嵁琛ュ叏              鈹?鈹? 鈹?鍟嗗搧淇℃伅锛堜粠鍟嗗搧涓績琛ラ綈锛?  鈹?鈹? 鈹?鐢ㄦ埛淇℃伅锛堜粠鐢ㄦ埛涓績琛ラ綈锛?  鈹?鈹? 鈹?浠锋牸鏍￠獙涓庝慨姝?            鈹?鈹? 鈹?榛樿鍊煎～鍏?                鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?     鈹?     鈻?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?Step 3: 瑙勫垯鏍￠獙              鈹?鈹? 鈹?蹇呭～瀛楁妫€鏌?              鈹?鈹? 鈹?浠锋牸涓€鑷存€ф牎楠?            鈹?鈹? 鈹?搴撳瓨鍙敤鎬ф鏌?            鈹?鈹? 鈹?椋庢帶棰勬                   鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?     鈹?     鈻?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?Step 4: 涓氬姟绾胯矾鐢?           鈹?鈹? 娓犻亾绫诲瀷 + 鍟嗗搧绫荤洰           鈹?鈹? 鈫?business_type锛坋commerce/ 鈹?鈹?   locallife/b2b锛?          鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?     鈹?     鈻?鏍囧噯鍖栬鍗?鈫?鍐欏叆 order 琛?```

#### 4.2 鏍囧噯鍖栬鍗曟ā鍨?
```java
/**
 * 鏍囧噯鍖栬鍗曪紙閫傞厤鍣ㄨ緭鍑猴紝涓彴鏍稿績杈撳叆锛? */
@Data
@Builder
public class NormalizedOrder {
    // ===== 娓犻亾婧簮 =====
    private String channelType;           // 娓犻亾绫诲瀷锛坱mall/jd/pdd/douyin/...锛?    private String channelOrderNo;        // 娓犻亾渚ц鍗曞彿
    private String channelShopId;         // 娓犻亾搴楅摵 ID

    // ===== 涔板淇℃伅 =====
    private String channelBuyerId;        // 娓犻亾渚т拱瀹?ID
    private String receiverName;          // 鏀惰揣浜?    private String receiverPhone;         // 鏀惰揣浜虹數璇?    private String receiverAddress;       // 鏀惰揣鍦板潃
    private String buyerRemark;           // 涔板澶囨敞

    // ===== 鍟嗗搧淇℃伅 =====
    private List<NormalizedOrderItem> orderItems;

    // ===== 閲戦淇℃伅 =====
    private Long totalAmount;             // 鎬婚噾棰濓紙鍒嗭級
    private Long freightAmount;           // 杩愯垂锛堝垎锛?    private Long discountAmount;          // 浼樻儬閲戦锛堝垎锛?    private Long payAmount;               // 瀹炰粯閲戦锛堝垎锛?
    // ===== 璁㈠崟淇℃伅 =====
    private OrderStatus status;           // 涓彴璁㈠崟鐘舵€?    private LocalDateTime orderTime;      // 涓嬪崟鏃堕棿
    private String businessType;          // 涓氬姟绾匡紙鐢辫矾鐢辫鍒欏喅瀹氾級

    // ===== 鍘熷鏁版嵁 =====
    private String rawJson;               // 娓犻亾鍘熷璇锋眰 JSON锛堝綊妗ｇ敤锛?}

@Data
@Builder
public class NormalizedOrderItem {
    private String skuId;                 // 娓犻亾 SKU ID
    private String productName;           // 鍟嗗搧鍚嶇О
    private String productImage;          // 鍟嗗搧鍥剧墖
    private Integer quantity;             // 鏁伴噺
    private Long unitPrice;               // 鍗曚环锛堝垎锛?    private Long totalPrice;              // 灏忚锛堝垎锛?}
```

#### 4.3 鏁版嵁琛ュ叏鏈嶅姟

```java
/**
 * 鏍囧噯鍖栧紩鎿?鈥?鏁版嵁琛ュ叏
 * 琛ラ綈娓犻亾鏃犳硶鎻愪緵鐨勫唴閮ㄥ瓧娈? */
@Component
public class OrderEnrichmentService {

    private final ProductClient productClient;
    private final UserClient userClient;

    public NormalizedOrder enrich(NormalizedOrder order) {
        // 1. 鍟嗗搧淇℃伅琛ュ叏锛圫KU ID 鈫?鍐呴儴 product_id + 绫荤洰锛?        for (NormalizedOrderItem item : order.getOrderItems()) {
            SkuInfo skuInfo = productClient.getSkuInfo(item.getSkuId());
            if (skuInfo != null) {
                item.setProductId(skuInfo.getProductId());
                item.setCategoryId(skuInfo.getCategoryId());
            }
        }

        // 2. 涓氬姟绫诲瀷鎺ㄧ畻锛堟牴鎹笭閬?+ 鍟嗗搧绫荤洰锛?        if (order.getBusinessType() == null) {
            order.setBusinessType(resolveBusinessType(order));
        }

        return order;
    }

    private String resolveBusinessType(NormalizedOrder order) {
        // 浠?Apollo 璇诲彇璺敱瑙勫垯
        RouteRule rule = channelRouter.match(order.getChannelType(), order.getOrderItems());
        return rule != null ? rule.getBusinessType() : "ecommerce";
    }
}
```

### 5. 娓犻亾娉ㄥ唽琛ㄤ笌璺敱瑙勫垯

#### 5.1 Apollo 娓犻亾閰嶇疆

```yaml
# Namespace: channel.config

# ===== 娓犻亾瀹氫箟 =====
channels:
  - type: tmall
    name: "澶╃尗"
    adapter: tmall
    authType: hmac_sha256
    pushMode: webhook              # Push 妯″紡锛歸ebhook锛堝疄鏃舵帹閫侊級
    webhookPath: /channel/tmall/webhook/order
    statusSyncSupported: true      # 鏀寔鐘舵€佸洖鍐?    inventorySyncSupported: false  # 涓嶆敮鎸佸簱瀛樺悓姝?
  - type: jd
    name: "浜笢"
    adapter: jd
    authType: hmac_sha256
    pushMode: webhook
    webhookPath: /channel/jd/webhook/order
    statusSyncSupported: true
    inventorySyncSupported: true

  - type: douyin
    name: "鎶栭煶"
    adapter: douyin
    authType: oauth2
    pushMode: webhook
    webhookPath: /channel/douyin/webhook/order
    statusSyncSupported: true
    inventorySyncSupported: false

  - type: pdd
    name: "鎷煎澶?
    adapter: pdd
    authType: oauth2
    pushMode: pull                  # Pull 妯″紡锛氬畾鏃舵媺鍙?    pullCron: "0 */5 * * * *"      # 姣?5 鍒嗛挓鎷夊彇涓€娆?    statusSyncSupported: true
    inventorySyncSupported: false

  - type: wechat_miniapp
    name: "寰俊灏忕▼搴?
    adapter: wechat_miniapp
    authType: session_key
    pushMode: webhook
    webhookPath: /channel/wechat/webhook/order
    statusSyncSupported: false
    inventorySyncSupported: false

  - type: pos
    name: "绾夸笅闂ㄥ簵POS"
    adapter: pos
    authType: api_key
    pushMode: pull
    pullCron: "0 */1 * * * *"      # 姣忓垎閽熸媺鍙栦竴娆?    statusSyncSupported: true
    inventorySyncSupported: true
```

#### 5.2 璺敱瑙勫垯閰嶇疆

```yaml
# Namespace: channel.route

# ===== 娓犻亾 鈫?涓氬姟绾胯矾鐢辫鍒?=====
routingRules:
  # 澶╃尗锛氶粯璁?鈫?鐢靛晢
  - channelType: tmall
    defaultBusinessType: ecommerce
    # 鐗规畩绫荤洰 鈫?鍏朵粬涓氬姟绾?    overrides:
      - categoryId: "local_life"
        businessType: locallife

  # 浜笢锛氶粯璁?鈫?鐢靛晢
  - channelType: jd
    defaultBusinessType: ecommerce

  # 鎶栭煶锛氭寜绫荤洰璺敱
  - channelType: douyin
    defaultBusinessType: ecommerce
    overrides:
      - categoryId: "local_life"
        businessType: locallife

  # 鎷煎澶氾細榛樿 鈫?鐢靛晢
  - channelType: pdd
    defaultBusinessType: ecommerce

  # POS 闂ㄥ簵锛氶粯璁?鈫?鏈湴鐢熸椿
  - channelType: pos
    defaultBusinessType: locallife

# ===== 娓犻亾搴楅摵璁よ瘉 =====
shops:
  - channelType: tmall
    shopId: "tmall_001"
    shopName: "瀹樻柟鏃楄埌搴?
    appKey: "tmall_app_key_xxx"
    enabled: true
    qpsLimit: 500

  - channelType: jd
    shopId: "jd_001"
    shopName: "浜笢鑷惀鏃楄埌搴?
    appKey: "jd_app_key_xxx"
    enabled: true
    qpsLimit: 500
```

#### 5.3 娓犻亾璺敱鏍稿績绫?
```java
/**
 * 娓犻亾璺敱鍣? * 鏍规嵁娓犻亾绫诲瀷 + 鍟嗗搧绫荤洰璺敱鍒板搴斾笟鍔＄嚎
 */
@Component
public class ChannelRouter {

    private final ApolloClient apollo;

    /**
     * 鍖归厤璺敱瑙勫垯
     */
    public RouteRule match(String channelType, List<NormalizedOrderItem> items) {
        // 浠?Apollo 璇诲彇璺敱閰嶇疆
        List<RouteRule> rules = loadRouteRules();
        String businessType = null;

        for (RouteRule rule : rules) {
            if (rule.getChannelType().equals(channelType)) {
                // 妫€鏌ユ槸鍚︽湁绫荤洰瑕嗙洊
                for (NormalizedOrderItem item : items) {
                    String override = rule.getOverrides().get(item.getCategoryId());
                    if (override != null) {
                        businessType = override;
                        break;
                    }
                }
                // 浣跨敤榛樿涓氬姟绾?                if (businessType == null) {
                    businessType = rule.getDefaultBusinessType();
                }
                return new RouteRule(channelType, businessType);
            }
        }

        // 鏈尮閰?鈫?榛樿璧扮數鍟?        return new RouteRule(channelType, "ecommerce");
    }

    private List<RouteRule> loadRouteRules() {
        String json = apollo.getConfig("channel.route", "routingRules");
        return JSON.parseArray(json, RouteRule.class);
    }
}
```

### 6. 鍙屽悜鐘舵€佸悓姝?
#### 6.1 鐘舵€佸悓姝ヤ簨浠舵祦

```
璁㈠崟鐘舵€佸彉鏇达紙order-core锛?     鈹?     鈻?鍙戝竷 OrderStatusChangedEvent锛圧ocketMQ锛?     鈹?     鈻?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?ChannelStatusSyncConsumer               鈹?鈹?鈶?浠庝簨浠朵腑鎻愬彇 channelOrderNo + status   鈹?鈹?鈶?鏌ヨ channel_raw_order 鑾峰彇娓犻亾淇℃伅     鈹?鈹?鈶?璋冪敤瀵瑰簲 Adapter.syncStatus()         鈹?鈹?鈶?璁板綍鍚屾缁撴灉鍒?channel_sync_log        鈹?鈹?鈶?鎴愬姛 鈫?ACK    澶辫触 鈫?閲嶈瘯             鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?     鈹?                          鈹?     鈻?                          鈻?娓犻亾 API 鍚屾鎴愬姛           閲嶈瘯闃熷垪 鈫?鎸囨暟閫€閬?                                鈹?                          鈹屸攢鈹€鈹€鈹€鈹€鈹粹攢鈹€鈹€鈹€鈹€鈹?                          鈹?閲嶈瘯 > 5娆? 鈹?                          鈹斺攢鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹?                                鈻?                           DLQ锛堟淇￠槦鍒楋級
                                鈹?                          鈹屸攢鈹€鈹€鈹€鈹€鈹粹攢鈹€鈹€鈹€鈹€鈹?                          鈹?XXL-Job    鈹?                          鈹?姣忓皬鏃跺厹搴? 鈹?                          鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?```

#### 6.2 鍚屾鏃ュ織琛?
```sql
-- 娓犻亾鍚屾鏃ュ織
CREATE TABLE `channel_sync_log` (
    `id` BIGINT AUTO_INCREMENT COMMENT '鑷 ID',
    `channel_type` VARCHAR(32) NOT NULL COMMENT '娓犻亾绫诲瀷',
    `channel_shop_id` VARCHAR(64) NOT NULL COMMENT '娓犻亾搴楅摵 ID',
    `channel_order_no` VARCHAR(128) NOT NULL COMMENT '娓犻亾璁㈠崟鍙?,
    `order_no` VARCHAR(32) NOT NULL COMMENT '涓彴璁㈠崟鍙?,
    `sync_type` VARCHAR(32) NOT NULL COMMENT '鍚屾绫诲瀷锛歋TATUS / LOGISTICS / INVENTORY',
    `sync_data` JSON NOT NULL COMMENT '鍚屾鐨勬暟鎹唴瀹?,
    `sync_status` VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT '鐘舵€侊細PENDING / SUCCESS / FAILED',
    `error_message` TEXT COMMENT '澶辫触鍘熷洜',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '宸查噸璇曟鏁?,
    `next_retry_time` DATETIME(3) COMMENT '涓嬫閲嶈瘯鏃堕棿',
    `gmt_create` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_channel_order` (`channel_type`, `channel_order_no`),
    KEY `idx_status_retry` (`sync_status`, `next_retry_time`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='娓犻亾鍚屾鏃ュ織';
```

#### 6.3 鍚屾娑堣垂鑰?
```java
/**
 * 娓犻亾鐘舵€佸悓姝ユ秷璐硅€? * 鐩戝惉璁㈠崟浜嬩欢锛屽悓姝ュ埌澶栭儴娓犻亾
 */
@Component
@RocketMQMessageListener(topic = "ORDER_STATUS_CHANGED", consumerGroup = "channel-sync")
public class ChannelStatusSyncConsumer implements RocketMQListener<OrderStatusChangedEvent> {

    private final ChannelAdapterRegistry adapterRegistry;
    private final ChannelSyncLogRepository syncLogRepository;

    @Override
    public void onMessage(OrderStatusChangedEvent event) {
        // 1. 鏌ヨ娓犻亾淇℃伅
        ChannelOrder channelOrder = channelOrderRepository.findByOrderNo(event.getOrderNo());
        if (channelOrder == null || channelOrder.getChannelType() == null) {
            return; // 闈炴笭閬撹鍗曪紝鏃犻渶鍚屾
        }

        // 2. 鑾峰彇閫傞厤鍣?        ChannelAdapter adapter = adapterRegistry.getAdapter(channelOrder.getChannelType());

        // 3. 鍚屾鐘舵€佸埌娓犻亾
        Map<String, Object> extra = new HashMap<>();
        extra.put("logisticsNo", event.getLogisticsNo());
        extra.put("logisticsCompany", event.getLogisticsCompany());

        try {
            ChannelSyncResult result = adapter.syncStatus(
                    channelOrder.getChannelOrderNo(),
                    event.getNewStatus(),
                    extra);

            // 4. 璁板綍鍚屾鏃ュ織
            syncLogRepository.save(ChannelSyncLog.builder()
                    .channelType(channelOrder.getChannelType())
                    .channelShopId(channelOrder.getChannelShopId())
                    .channelOrderNo(channelOrder.getChannelOrderNo())
                    .orderNo(event.getOrderNo())
                    .syncType("STATUS")
                    .syncStatus(result.isSuccess() ? "SUCCESS" : "FAILED")
                    .build());

            if (!result.isSuccess()) {
                throw new SyncFailedException("Channel sync failed: " + result.getErrorMsg());
            }
        } catch (Exception e) {
            // 鎶涘嚭寮傚父 鈫?RocketMQ 閲嶈瘯
            throw new RuntimeException("Channel status sync failed", e);
        }
    }
}
```

#### 6.4 XXL-Job 鍏滃簳瀵硅处

```java
/**
 * 娓犻亾璁㈠崟瀵硅处 Job
 * 姣忔棩鍑屾櫒鎵ц锛岀‘淇濇笭閬撹鍗曚笌涓彴璁㈠崟涓€鑷? */
@Component
public class ChannelReconciliationJob {

    private final ChannelAdapterRegistry adapterRegistry;
    private final ChannelOrderRepository channelOrderRepository;

    @XXLJob("channelReconciliation")
    public void reconcile() {
        for (String channelType : adapterRegistry.getSupportedChannels()) {
            ChannelAdapter adapter = adapterRegistry.getAdapter(channelType);

            if (!(adapter instanceof PullableChannelAdapter)) {
                continue; // 涓嶆敮鎸?Pull 鐨勬笭閬撹烦杩?            }

            // 鎷夊彇娓犻亾鏄ㄦ棩璁㈠崟
            LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();
            LocalDateTime yesterdayEnd = LocalDate.now().atStartOfDay();

            List<RawChannelRequest> channelOrders = adapter.pullOrders(
                    null, yesterdayStart, yesterdayEnd);

            // 姣斿涓彴璁㈠崟
            for (RawChannelRequest rawOrder : channelOrders) {
                String channelOrderNo = rawOrder.getChannelOrderNo();
                ChannelOrder localOrder = channelOrderRepository
                        .findByChannelOrderNo(channelType, channelOrderNo);

                if (localOrder == null) {
                    // 涓彴缂哄け 鈫?琛ュ綍鍏?                    log.warn("Missing order in OMP: {}/{}", channelType, channelOrderNo);
                    compensateCreateOrder(adapter, rawOrder);
                }
            }
        }
    }
}
```

### 7. 鍘熷璁㈠崟瀛樺偍

#### 7.1 `channel_raw_order` 琛?
```sql
-- 娓犻亾鍘熷璁㈠崟琛紙鏍囧噯鍖栧墠鏁版嵁瀛樻。锛岀敤浜庡璐﹀拰闂鎺掓煡锛?CREATE TABLE `channel_raw_order` (
    `id` BIGINT AUTO_INCREMENT COMMENT '鑷 ID',
    `channel_type` VARCHAR(32) NOT NULL COMMENT '娓犻亾绫诲瀷',
    `channel_order_no` VARCHAR(128) NOT NULL COMMENT '娓犻亾璁㈠崟鍙?,
    `channel_shop_id` VARCHAR(64) NOT NULL COMMENT '娓犻亾搴楅摵 ID',
    `order_no` VARCHAR(32) DEFAULT NULL COMMENT '鏍囧噯鍖栧悗鐨勪腑鍙拌鍗曞彿锛堝彲涓虹┖锛屽墠缃牎楠屽け璐ユ椂鍙兘鏃犱腑鍙拌鍗曪級',
    `raw_data` JSON NOT NULL COMMENT '娓犻亾鍘熷璇锋眰鏁版嵁锛堝叏閲忥級',
    `data_hash` VARCHAR(64) NOT NULL COMMENT '鍘熷鏁版嵁 SHA256 鎽樿锛堥槻绡℃敼鏍￠獙锛?,
    `normalize_status` VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT '鏍囧噯鍖栫姸鎬侊細PENDING / SUCCESS / FAILED',
    `normalize_error` TEXT COMMENT '鏍囧噯鍖栧け璐ュ師鍥?,
    `business_type` VARCHAR(32) COMMENT '璺敱鍚庣殑涓氬姟绾?,
    `gmt_create` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_order` (`channel_type`, `channel_order_no`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_create_time` (`gmt_create`),
    KEY `idx_normalize_status` (`normalize_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='娓犻亾鍘熷璁㈠崟';

-- 鎸夋笭閬撴寜鏈堝垎鍖猴紙浠ュぉ鐚负渚嬶級
-- ALTER TABLE channel_raw_order PARTITION BY RANGE (TO_DAYS(gmt_create)) (
--   PARTITION p_tmall_202606 VALUES LESS THAN (TO_DAYS('2026-07-01')),
--   PARTITION p_tmall_202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
-- );
```

#### 7.2 鏍囧噯鍖栧悗鏁版嵁鏄犲皠

娓犻亾璁㈠崟缁忚繃鏍囧噯鍖栧悗鍐欏叆 `order` 琛紙鐜版湁缁熶竴璁㈠崟妯″瀷锛夛紝鍚屾椂鏂板瀛楁璁板綍娓犻亾婧簮淇℃伅锛?
```sql
-- order 琛ㄦ柊澧炲瓧娈碉紙鐢ㄤ簬娓犻亾璁㈠崟婧簮锛?ALTER TABLE `order` ADD COLUMN `channel_type` VARCHAR(32) DEFAULT NULL COMMENT '娓犻亾绫诲瀷锛坱mall/jd/pdd/...锛? AFTER `business_type`;
ALTER TABLE `order` ADD COLUMN `channel_order_no` VARCHAR(128) DEFAULT NULL COMMENT '娓犻亾渚ц鍗曞彿' AFTER `channel_type`;
ALTER TABLE `order` ADD COLUMN `channel_shop_id` VARCHAR(64) DEFAULT NULL COMMENT '娓犻亾搴楅摵 ID' AFTER `channel_order_no`;

-- 绱㈠紩
ALTER TABLE `order` ADD INDEX `idx_channel_order` (`channel_type`, `channel_order_no`);
```

### 8. 娓犻亾绠＄悊鍚庡彴

#### 8.1 绠＄悊 API

```java
/**
 * 娓犻亾绠＄悊 API锛堣繍钀ュ悗鍙颁娇鐢級
 */
@RestController
@RequestMapping("/admin/v1/channels")
public class ChannelAdminController {

    private final ChannelConfigService configService;

    // 鑾峰彇鎵€鏈夋笭閬撳垪琛?    @GetMapping("")
    public List<ChannelConfigVO> listChannels() { ... }

    // 鑾峰彇娓犻亾璇︽儏
    @GetMapping("/{channelType}")
    public ChannelConfigVO getChannel(@PathVariable String channelType) { ... }

    // 鍚敤/绂佺敤娓犻亾
    @PutMapping("/{channelType}/status")
    public void toggleChannel(@PathVariable String channelType,
                              @RequestParam boolean enabled) { ... }

    // 娓犻亾搴楅摵绠＄悊
    @GetMapping("/{channelType}/shops")
    public List<ChannelShopVO> listShops(@PathVariable String channelType) { ... }

    @PostMapping("/{channelType}/shops")
    public void addShop(@PathVariable String channelType,
                        @RequestBody ChannelShopCreateRequest request) { ... }

    // 娓犻亾鍚屾鐘舵€佹煡璇?    @GetMapping("/{channelType}/sync-status")
    public ChannelSyncStatusVO getSyncStatus(@PathVariable String channelType) { ... }

    // 鎵嬪姩瑙﹀彂娓犻亾瀵硅处
    @PostMapping("/{channelType}/reconcile")
    public void triggerReconcile(@PathVariable String channelType) { ... }

    // 娓犻亾璁㈠崟鏌ヨ锛堟寜娓犻亾鍗曞彿锛?    @GetMapping("/orders")
    public PageResult<ChannelOrderVO> queryChannelOrders(
            @RequestParam String channelType,
            @RequestParam(required = false) String channelOrderNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) { ... }
}
```

#### 8.2 杩愯惀浜哄憳宸ヤ綔娴?
```
杩愯惀閰嶇疆娓犻亾
鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
  鈶?娓犻亾鎺ュ叆鐢宠
     鈹溾攢鈹€ 杩愯惀鍦ㄥ悗鍙版彁浜ゃ€屾柊娓犻亾鎺ュ叆鐢宠銆?     鈹溾攢鈹€ 濉啓娓犻亾鍚嶇О銆佸鎺ユ柟寮忋€佽仈绯讳汉
     鈹斺攢鈹€ 鎶€鏈瘎浼帮紙寮€鍙戝伐鏃躲€佹笭閬撴枃妗ｅ畬澶囧害锛?          鈹?          鈻?  鈶?娓犻亾閫傞厤寮€鍙?     鈹溾攢鈹€ 瀹炵幇 ChannelAdapter SPI锛堝瓧娈垫槧灏勩€佺鍚嶃€佺姸鎬佹槧灏勶級
     鈹溾攢鈹€ 瀹炵幇 ChannelAuthProvider SPI锛堣璇侀€昏緫锛?     鈹溾攢鈹€ 閰嶇疆 Apollo 娓犻亾瀹氫箟鍜岃矾鐢辫鍒?     鈹斺攢鈹€ 娴嬭瘯鐜鑱旇皟閫氳繃
          鈹?          鈻?  鈶?鐏板害涓婄嚎
     鈹溾攢鈹€ Apollo 璁剧疆 enabled: true锛堢伆搴?10% 搴楅摵锛?     鈹溾攢鈹€ 瑙傚療娓犻亾璁㈠崟鎺ュ叆鎴愬姛鐜?     鈹溾攢鈹€ 纭鏍囧噯鍖栨纭巼锛堥噰鏍峰璐︼級
     鈹斺攢鈹€ 鐏板害 3 澶╂棤寮傚父
          鈹?          鈻?  鈶?鍏ㄩ噺涓婄嚎
     鈹溾攢鈹€ Apollo 璁剧疆 enabled: true锛堝叏閲忓簵閾猴級
     鈹溾攢鈹€ 閰嶇疆闄愭祦闃堝€硷紙鏍规嵁娓犻亾瀹归噺锛?     鈹溾攢鈹€ 閰嶇疆瀵硅处 Job锛堟瘡鏃ュ噷鏅級
     鈹斺攢鈹€ 鍔犲叆鐩戞帶澶х洏
```

### 9. 鐩戞帶涓庡憡璀?
#### 9.1 Prometheus 鎸囨爣

```yaml
metrics:
  - name: channel_ingestion_total
    type: counter
    labels: [channel_type, status]  # status: received / normalized / failed
    help: "娓犻亾璁㈠崟鎺ュ叆鎬婚噺"

  - name: channel_ingestion_duration_ms
    type: histogram
    labels: [channel_type]
    help: "娓犻亾璁㈠崟鎺ュ叆澶勭悊寤惰繜"
    buckets: [50, 100, 200, 500, 1000, 2000, 5000]

  - name: channel_sync_total
    type: counter
    labels: [channel_type, sync_type, status]  # sync_type: status / logistics / inventory
    help: "娓犻亾鐘舵€佸悓姝ユ€婚噺"

  - name: channel_sync_failed_total
    type: counter
    labels: [channel_type, sync_type]
    help: "娓犻亾鐘舵€佸悓姝ュけ璐ユ鏁?

  - name: channel_sync_queue_depth
    type: gauge
    labels: [channel_type]
    help: "娓犻亾鍚屾闃熷垪绉帇娣卞害"

  - name: channel_raw_order_backlog
    type: gauge
    labels: [channel_type, normalize_status]
    help: "寰呮爣鍑嗗寲澶勭悊鐨勫師濮嬭鍗曠Н鍘嬫暟"

  - name: channel_reconciliation_result
    type: gauge
    labels: [channel_type, result]  # result: matched / missing_in_omp / missing_in_channel / amount_mismatch
    help: "娓犻亾瀵硅处缁撴灉锛堟瘡鏃ワ級"
```

#### 9.2 鍛婅瑙勫垯

```yaml
alerts:
  - name: ChannelIngestionFailureRateHigh
    description: "娓犻亾璁㈠崟鎺ュ叆澶辫触鐜?> 1%锛?min 绐楀彛锛?
    severity: P1
    condition: "rate(channel_ingestion_total{status='failed'}[5m]) / rate(channel_ingestion_total[5m]) > 0.01"

  - name: ChannelSyncFailureRateHigh
    description: "娓犻亾鐘舵€佸悓姝ュけ璐ョ巼 > 5%锛?min 绐楀彛锛?
    severity: P2
    condition: "rate(channel_sync_failed_total[5m]) / rate(channel_sync_total[5m]) > 0.05"

  - name: ChannelSyncQueueGrowing
    description: "娓犻亾鍚屾闃熷垪绉帇鎸佺画澧為暱 > 1000"
    severity: P2
    condition: "channel_sync_queue_depth > 1000"

  - name: ChannelRawOrderBacklog
    description: "娓犻亾鍘熷璁㈠崟寰呭鐞嗙Н鍘?> 10000"
    severity: P2
    condition: "channel_raw_order_backlog{normalize_status='PENDING'} > 10000"

  - name: ChannelReconciliationMismatch
    description: "娓犻亾瀵硅处鍙戠幇宸紓锛? 10 绗旓級"
    severity: P3
    condition: "channel_reconciliation_result{result!='matched'} > 10"
```

---

## 瀹炴柦璁″垝

| 闃舵 | 鏍稿績浠诲姟 | 宸ユ椂 | 浜у嚭 |
|------|---------|------|------|
| **P1 鍩虹璁炬柦** | Channel Gateway 鐙珛閮ㄧ讲 + Apollo 娓犻亾閰嶇疆鍛藉悕绌洪棿 + 娓犻亾璁よ瘉 SPI 妗嗘灦 | 3d | Channel Gateway 涓婄嚎 |
| **P2 鏍稿績娴佺▼** | ChannelAdapter SPI + 璁㈠崟鏍囧噯鍖栧紩鎿?+ channel_raw_order 琛?+ ChannelRouter | 4d | 娓犻亾鎺ュ叆鏍稿績閾捐矾璺戦€?|
| **P3 娓犻亾閫傞厤** | 澶╃尗閫傞厤鍣?+ 浜笢閫傞厤鍣紙绀轰緥瀹炵幇锛? 绔埌绔仈璋?| 3d | 棣栨壒 2 涓笭閬撴帴鍏?|
| **P4 鐘舵€佸悓姝?* | 寮傛鐘舵€佸悓姝?Consumer + channel_sync_log + 鎸囨暟閫€閬块噸璇?+ DLQ | 2d | 鐘舵€佸洖鍐欒兘鍔?|
| **P5 瀵硅处琛ュ伩** | XXL-Job 娓犻亾瀵硅处 Job + Pull 妯″紡閫傞厤鍣?+ 琛ュ崟閫昏緫 | 2d | 鏁版嵁涓€鑷存€т繚闅?|
| **P6 绠＄悊鍚庡彴** | 娓犻亾绠＄悊 API + 杩愯惀鍚庡彴椤甸潰 + 娓犻亾鐩戞帶澶х洏 | 2d | 杩愯惀鍙嚜鍔╃鐞?|
| **P7 瀹夊叏鍔犲浐** | 娓犻亾璁よ瘉瀹夊叏瀹¤ + 鏁版嵁鍔犵闃茬鏀?+ 娓犻亾鎺ュ叆瑙勮寖鏂囨。 | 1d | 瀹夊叏璇勫閫氳繃 |

**鎬昏锛?7 浜哄ぉ**

---

## 涓婄嚎妫€鏌ユ竻鍗?
### 娓犻亾鎺ュ叆
- [ ] Channel Gateway 鐙珛閮ㄧ讲锛屼笌 IGW/Ext GW 娴侀噺闅旂
- [ ] 鑷冲皯 1 涓笭閬擄紙澶╃尗/浜笢锛夌鍒扮鑱旇皟閫氳繃
- [ ] 娓犻亾 Webhook 绛惧悕璁よ瘉楠岃瘉閫氳繃锛堥槻浼€犺姹傦級
- [ ] 娓犻亾璁㈠崟鏍囧噯鍖栬浆鎹㈤獙璇侊紙瀛楁鏄犲皠瀹屾暣銆侀噾棰濅竴鑷达級
- [ ] 寮傚父鏁版嵁鏍￠獙鎷掔粷鏈哄埗楠岃瘉锛堝瓧娈电己澶?閲戦涓嶇 鈫?鎷掔粷 + 鍛婅锛?
### 鏁版嵁瀛樺偍
- [ ] `channel_raw_order` 琛ㄥ缓琛ㄥ畬鎴愶紝鏁版嵁鍐欏叆楠岃瘉
- [ ] 鍘熷璁㈠崟鏁版嵁瀹屾暣鎬ф牎楠岋紙SHA256 鎽樿锛?- [ ] `order` 琛ㄦ柊澧炲瓧娈碉紙channel_type / channel_order_no / channel_shop_id锛夐獙璇?
### 鐘舵€佸悓姝?- [ ] 璁㈠崟鐘舵€佸彉鏇?鈫?MQ 鈫?娓犻亾 API 鍚屾閾捐矾楠岃瘉
- [ ] 娓犻亾 API 瓒呮椂/澶辫触 鈫?閲嶈瘯闃熷垪 鈫?DLQ 娴佺▼楠岃瘉
- [ ] 鍚屾澶辫触璁板綍鍦?channel_sync_log 涓彲杩芥函

### 瀵硅处琛ュ伩
- [ ] XXL-Job 娓犻亾瀵硅处浠诲姟閰嶇疆楠岃瘉
- [ ] 缂哄け璁㈠崟鑷姩琛ュ綍娴佺▼楠岃瘉
- [ ] 瀵硅处宸紓鍛婅楠岃瘉

### 杩愯惀绠＄悊
- [ ] 娓犻亾绠＄悊 API CRUD 楠岃瘉
- [ ] Apollo 娓犻亾閰嶇疆鐑敓鏁堥獙璇侊紙鍚敤/绂佺敤娓犻亾銆佷慨鏀归檺娴侀槇鍊硷級
- [ ] 娓犻亾璁㈠崟鏌ヨ鍔熻兘鍙敤

### 鐩戞帶
- [ ] 娓犻亾鎺ュ叆/鍚屾/瀵硅处鎸囨爣鎺ュ叆 Prometheus
- [ ] Grafana 娓犻亾鐩戞帶鐪嬫澘閰嶇疆瀹屾垚
- [ ] 鍛婅瑙勫垯閰嶇疆楠岃瘉锛堟帴鍏ュけ璐ョ巼銆佸悓姝ョН鍘嬨€佸璐﹀樊寮傦級

---

## 涓庡叾浠?ADR 鐨勫叧绯?
| ADR | 鍏崇郴 |
|-----|------|
| **ADR-017**锛堜笟鍔＄嚎鐗╃悊闅旂锛?| 娓犻亾璺敱缁撴灉鍐冲畾 `business_type`锛屽悗缁墿鐞嗛殧绂诲悗娓犻亾璁㈠崟鎸変笟鍔＄嚎鍐欏叆鐙珛琛?|
| **ADR-025**锛堝閮ㄧ綉鍏筹級 | Channel Gateway 涓?External Gateway 鑱岃矗涓嶅悓锛氬墠鑰呭鐞嗘笭閬撴祦閲忥紝鍚庤€呭鐞嗙涓夋柟寮€鍙戣€呮祦閲?|
| **ADR-030**锛堝叏灞€骞傜瓑妗嗘灦锛?| 娓犻亾 Webhook 鍙兘閲嶅鎺ㄩ€侊紝鎺ュ叆灞傚鐢?Idempotency-Key 骞傜瓑鏍￠獙 |
| **ADR-031**锛堟暟鎹敓鍛藉懆鏈燂級 | `channel_raw_order` 鍘熷鏁版嵁鎸夋硶瑙勮姹傚喅瀹氫繚鐣欏懆鏈?|
| **ADR-033**锛圵ebhook 绯荤粺锛?| 娓犻亾 Webhook 鎺ユ敹绔笌 ADR-033 澶栭儴鎺ㄩ€?Webhook 鏄笉鍚屾柟鍚戠殑鏁版嵁娴?|
| **ADR-022**锛堝叏閾捐矾鐏板害锛?| 鏂版笭閬撻€傞厤鍣ㄧ伆搴︽椂锛岄€氳繃鐏板害鏍囪闅旂娓犻亾娴侀噺 |
| **context-diagram.puml** | 鏂板澶栭儴閿€鍞笭閬撶郴缁熻竟鐣?|
| **container-diagram.puml** | 鏂板 Channel Gateway + channel-adapter 瀹瑰櫒 |

---

## 闄勫綍锛氫笉鏀寔鍝簺娓犻亾鑳藉姏

浠ヤ笅鑳藉姏灞炰簬閿€鍞笭閬撳钩鍙拌嚜韬殑鍔熻兘锛?*涓嶅湪鏈?ADR 鑼冨洿鍐?*锛?
| 鑳藉姏 | 璇存槑 | 褰掑睘 |
|------|------|------|
| 娓犻亾鍟嗗搧涓婃灦 | 鍦ㄦ笭閬撳钩鍙板彂甯冨晢鍝?SPU | 娓犻亾杩愯惀 |
| 娓犻亾钀ラ攢娲诲姩 | 鍦ㄦ笭閬撳钩鍙拌缃弧鍑?鎶樻墸 | 娓犻亾杩愯惀 |
| 娓犻亾娴侀噺鑾峰彇 | 绔炰环鎺掑悕/骞垮憡鎶曟斁/鐩存挱闂村紩娴?| 娓犻亾杩愯惀 |
| 娓犻亾鏀粯澶勭悊 | 鏀粯瀹?寰俊鏀粯闆嗘垚鐢辨笭閬撹嚜韬畬鎴?| 娓犻亾骞冲彴 |
| 娓犻亾瀹㈡湇鍗虫椂閫氳 | 涔板涓庡晢瀹跺湪娓犻亾骞冲彴鐨勮亰澶╁姛鑳?| 娓犻亾骞冲彴 |

鏈?ADR 鑱氱劍鍦?*璁㈠崟鏁版嵁鐨勬帴鍏ャ€佹爣鍑嗗寲銆佽矾鐢卞拰鐘舵€佸悓姝?*鈥斺€斿嵆涓彴涓庢笭閬撲箣闂寸殑鏁版嵁浜ゆ崲灞傘€?