# ADR-022锛氬叏閾捐矾鐏板害鍙戝竷

## 鐘舵€?
宸叉帴鍙?
---

## 鑳屾櫙

### 鐜扮姸鍒嗘瀽

璁㈠崟涓彴褰撳墠鏀寔**鍗曟湇鍔＄伆搴﹀彂甯?*锛屾祦绋嬪畾涔夊湪 `docs/diagrams/canary-release.md`锛?
```
鍗曟湇鍔＄伆搴︽祦绋嬶紙鐜版湁锛?鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
Canary(1 Pod, 1%, 5min) 鈫?Batch 1(5%, 15min) 鈫?Batch 2(20%, 30min)
鈫?Batch 3(50%, 30min) 鈫?Full(100%)

鍥炴粴鏉′欢锛歟rror_rate > 0.1% || P99 > baseline + 20ms

娴侀噺鍒囧垎锛欼stio VirtualService weight
閰嶇疆绠＄悊锛欰pollo 鐏板害寮€鍏?```

璇ユ祦绋嬮拡瀵?*鍗曚竴鏈嶅姟**鐨勬柊鐗堟湰楠岃瘉鈥斺€旈儴缃?order-core v2.3.0 鐨勪竴涓?Pod锛孖stio 灏?1% 娴侀噺瀵煎叆璇?Pod锛岃瀵熸棤寮傚父鍚庨€愭鏀鹃噺銆?
### 瀛樺湪鐨勯棶棰?
褰撲竴娆￠渶姹傛秹鍙?**澶氫釜鏈嶅姟鍗忓悓鍙樻洿** 鏃讹紝鍗曟湇鍔＄伆搴︽毚闇插嚭浠ヤ笅闂锛?
| 闂 | 鎻忚堪 | 褰卞搷 |
|------|------|------|
| **鐗堟湰涓€鑷存€ф柇瑁?* | 鐏板害璇锋眰鍦?order-core 璧颁簡鐏板害鐗堟湰锛屼絾 Dubbo 璋冪敤 payment 鏃舵病鏈夋惡甯︾伆搴︽爣璁帮紝payment 璺敱鍒扮ǔ瀹氱増瀹炰緥 | 鐏板害閫昏緫涓庣ǔ瀹氶€昏緫娣疯窇锛屽彲鑳戒骇鐢熼敊璇暟鎹?|
| **鍏ㄩ摼璺笉鍙獙璇?* | 鏂扮粨绠楁祦绋嬮渶瑕?order-core + payment + inventory 鍚屾椂鍙樻洿锛屽崟鏈嶅姟鐏板害鍙兘楠岃瘉鍏朵腑涓€鐜?| 鏍稿績閾捐矾鏃犳硶鍏ㄩ噺楠岃瘉锛岄棶棰樺湪鍙戝竷鍚庢墠鍙戠幇 |
| **鐏板害鏁版嵁姹℃煋绋冲畾鐜** | 鐏板害璇锋眰浜х敓鐨勮鍗曟暟鎹贩鍏ョǔ瀹氱増 DB/Redis/MQ锛屾帓鏌ラ棶棰樻椂鏃犳硶鍖哄垎 | 鏁版嵁涓嶄竴鑷存帓鏌ュ洶闅?|
| **鍏煎鎬т笉鍙帶** | 鐏板害鐗堟湰璋冪敤浜嗙ǔ瀹氱増鐨勬柊鎺ュ彛锛屾垨绋冲畾鐗堟湰璋冪敤浜嗙伆搴︾増鐨勬柊鎺ュ彛 | 鎺ュ彛鍏煎鎬ч棶棰樺鑷?500 鎴栨暟鎹敊涔?|

### 鍏稿瀷澶氭湇鍔＄伆搴﹀満鏅?
```
鍦烘櫙 1锛氭柊缁撶畻娴佺▼ v2.3.0
  娑夊強鏈嶅姟锛歰rder-core + payment-core + inventory-service
  鍙樻洿鍐呭锛氭柊鏀粯鏂瑰紡銆佹柊搴撳瓨鎵ｅ噺閫昏緫銆佹柊璁㈠崟鐘舵€佹祦杞?
鍦烘櫙 2锛氭柊閫€娆炬祦绋?v2.4.0
  娑夊強鏈嶅姟锛歛ftersale-service + payment-core + order-core
  鍙樻洿鍐呭锛氬敭鍚庢祦绋嬮噸鏋勩€侀€€娆鹃摼璺紭鍖?
鍦烘櫙 3锛氬弻 11 鏂颁績閿€娴佺▼ v2.5.0
  娑夊強鏈嶅姟锛歱romotion-service + price-service + inventory-service
  鍙樻洿鍐呭锛氭柊浼樻儬璁＄畻瑙勫垯銆佸灞傛姌鎵ｅ彔鍔犮€佸簱瀛橀鍗犵瓥鐣?```

---

## 鍐崇瓥

**涓绘柟妗堬細Dubbo Filter + RpcContext 鏍囩浼犳挱 + 鑷畾涔?Dubbo Router**

**杈呭姪鏂规锛欼stio DestinationRule 鍋?K8s 鐗堟湰 subset 鍒囧垎**

### 鏂规瀵规瘮

| 缁村害 | 鏂规 A锛欴ubbo Filter锛堥€変腑锛?| 鏂规 B锛欼stio-only | 鏂规 C锛歂acos Metadata + 鑷畾涔?LoadBalancer |
|------|------|------|------|
| **鍘熺悊** | Gateway 璇嗗埆鐏板害璇锋眰 鈫?娉ㄥ叆 `grayTag` 鍒?Dubbo RpcContext 鈫?鑷畾涔?Dubbo Router 鏍规嵁 tag 璺敱鍒板搴旂増鏈疄渚?| Istio VirtualService 鏍规嵁 header `x-gray-tag` 璺敱锛屽畬鍏ㄥ湪 Service Mesh 灞傞潰瀹炵幇 | 鐏板害鐗堟湰鍦?Nacos 娉ㄥ唽鏃跺啓鍏?`version` metadata锛岃嚜瀹氫箟 LoadBalancer 鏍规嵁 metadata 鍖归厤璺敱 |
| **鍏ㄩ摼璺鐩?* | 鉁?鍏ㄩ摼璺紙鎵€鏈?Dubbo 璋冪敤鑷姩浼犻€掞級 | 鈿狅笍 浠?HTTP锛圖ubbo 鍗忚涓嬫爣绛鹃€忎紶涓嶆垚鐔燂級 | 鉂?闇€瑕侀澶栨満鍒惰法閾捐矾浼犻€?tag |
| **浠ｇ爜渚靛叆** | 涓紙3 涓?Filter SPI + 1 涓?Router SPI锛屾棤涓氬姟浠ｇ爜渚靛叆锛?| 闆?| 涓紙鏀归€?LoadBalancer 閰嶇疆锛?|
| **鐏板害瑙勫垯绮剧粏搴?* | 楂橈紙鐢ㄦ埛鐧藉悕鍗曘€佹瘮渚嬨€丠eader銆丳ath銆佸绛栫暐缁勫悎锛?| 浣庯紙浠呮敮鎸?header 鍖归厤锛?| 涓紙浠呮敮鎸?metadata 鍖归厤锛?|
| **Dubbo 鍏煎鎬?* | 鍘熺敓锛圖ubbo SPI 鏈哄埗锛?| 闇€ Istio 1.5+ 棰濆閰嶇疆 | 闇€鏀归€?Dubbo LoadBalancer |
| **涓?Apollo 闆嗘垚** | 澶╃劧锛團ilter/Router 鐩存帴璇诲彇 Apollo锛?| 闇€棰濆鍚屾鏈哄埗 | 闇€棰濆鍚屾鏈哄埗 |
| **杩愮淮澶嶆潅搴?* | 浣庯紙涓€娆￠厤缃紝鍏ㄦ湇鍔＄敓鏁堬級 | 楂橈紙姣忎釜鏈嶅姟闇€棰濆 VirtualService 閰嶇疆锛?| 涓?|

### 閫夊瀷鐞嗙敱

1. **璁㈠崟涓彴鍏ㄦ爤 Dubbo RPC**锛?00% 鏈嶅姟闂磋皟鐢ㄨ蛋 Dubbo锛夛紝鏂规 A 閫氳繃 Dubbo SPI Filter 鏈哄埗瑕嗙洊鎵€鏈夎皟鐢ㄩ摼璺紝涓嶄細閬楁紡浠讳綍鏈嶅姟
2. **Istio 瀹氫綅涓鸿緟鍔╁眰**鈥斺€旇礋璐?K8s 灞傞潰鐨勬祦閲忓垎娴侊紙version subset 闅旂銆佸彲瑙傛祴鎬э級锛屼絾涓嶈礋璐ｅ簲鐢ㄥ眰鐨勭伆搴?tag 鍐崇瓥
3. **Nacos metadata 浣滀负鐗堟湰淇℃伅娉ㄥ唽婧?*锛屼緵 Dubbo Router 鐨勫疄渚嬬瓫閫夊拰 Istio 鐨?version label 鍏卞悓浣跨敤锛岄伩鍏嶅厓鏁版嵁鍒嗘暎
4. **Apollo 宸叉槸涓彴閰嶇疆涓績**锛屾柟妗?A 鐨勭伆搴﹁鍒欙紙鐧藉悕鍗?姣斾緥/鐗堟湰鏄犲皠锛夊ぉ鐒朵笌 Apollo 闆嗘垚锛岄厤缃彉鏇寸绾х敓鏁?
---

## 璇︾粏璁捐

### 1. Gray Tag 鍏ㄧ敓鍛藉懆鏈?
```
Gray Tag 鍏ㄩ摼璺敓鍛藉懆鏈?鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
鍏ュ彛灞?(Spring Cloud Gateway)
  鈹?  鈹?鈶?璇嗗埆鐏板害璇锋眰锛堢煭璺垽瀹氾級
  鈹?  鈹溾攢鈹€ Header: x-gray-tag: force-v2.3.0  鈫?寮哄埗鐏板害
  鈹?  鈹溾攢鈹€ userId 鍛戒腑鐧藉悕鍗?                   鈫?鐢ㄦ埛鐏板害
  鈹?  鈹溾攢鈹€ hash(userId)%100 < grayPercentage   鈫?姣斾緥鐏板害
  鈹?  鈹斺攢鈹€ Header/Path 鍖归厤瑙勫垯                 鈫?瑙勫垯鐏板害
  鈹?  鈫?  鈹?鈶?鐢熸垚 GrayTag
  鈹?  鈹溾攢鈹€ grayTag = "v2.3.0-canary"
  鈹?  鈹溾攢鈹€ grayStrategy = "percentage:5"
  鈹?  鈹溾攢鈹€ traceId = 鍏宠仈 SkyWalking trace
  鈹?  鈹斺攢鈹€ timestamp = 鐢熸垚鏃堕棿
  鈹?  鈫?  鈹?鈶?鍐欏叆涓婁笅鏂?  鈹?  鈹溾攢鈹€ GrayContext.set(tag)                  鈫?ThreadLocal
  鈹?  鈹溾攢鈹€ RpcContext.setAttachment("grayTag")   鈫?Dubbo 闅愬紡浼犲弬
  鈹?  鈹溾攢鈹€ MDC.put("grayTag", ...)               鈫?鏃ュ織鍏宠仈
  鈹?  鈹斺攢鈹€ Response Header: x-gray-result         鈫?Debug
  鈹?  鈻?杞彂灞?(Dubbo Consumer 鈥?姣忎釜鏈嶅姟璋冪敤涓嬫父鏃?
  鈹?  鈹?鈶?GrayTagConsumerFilter锛圖ubbo Consumer SPI锛?  鈹?  鈹溾攢鈹€ 浠?GrayContext 璇诲彇 grayTag
  鈹?  鈹溾攢鈹€ 鍐欏叆 RpcContext.getClientAttachment()
  鈹?  鈹斺攢鈹€ 鎵ц invoker.invoke(invocation)
  鈹?  鈻?鎺ユ敹灞?(Dubbo Provider 鈥?姣忎釜鏈嶅姟琚皟鐢ㄦ椂)
  鈹?  鈹?鈶?GrayTagProviderFilter锛圖ubbo Provider SPI锛?  鈹?  鈹溾攢鈹€ 浠?RpcContext.getServerAttachment() 鍙栧嚭 grayTag
  鈹?  鈹溾攢鈹€ GrayContext.set(tag)                  鈫?寤虹珛鏈湇鍔＄伆搴︿笂涓嬫枃
  鈹?  鈹溾攢鈹€ 鎵ц invoker.invoke(invocation)
  鈹?  鈹斺攢鈹€ finally { GrayContext.clear() }
  鈹?  鈻?璺敱灞?(Dubbo Router 鈥?姣忎釜鏈嶅姟璋冪敤涓嬫父鏃?
  鈹?  鈹?鈶?GrayTagRouter锛圖ubbo RouterFactory SPI锛?  鈹?  鈹溾攢鈹€ 浠?GrayContext 鑾峰彇 grayTag
  鈹?  鈹溾攢鈹€ 鏌ヨ Apollo GrayRoutingRule
  鈹?  鈹溾攢鈹€ 绛涢€?Nacos metadata 涓?version = targetVersion 鐨勫疄渚?  鈹?  鈹溾攢鈹€ 鏃犲尮閰嶇伆搴﹀疄渚?鈫?闄嶇骇鍒扮ǔ瀹氱増锛堣褰?metrics锛?  鈹?  鈹斺攢鈹€ 璁板綍璺敱鍐崇瓥鍒?Prometheus
  鈹?  鈻?涓嬫父鏈嶅姟
  鈹?  鈹?鈶?閫掑綊浼犳挱锛堥噸澶嶆楠?鈶ｂ啋鈶も啋鈶ワ級
  鈹?  鈹溾攢鈹€ order-core 鈫?payment-core
  鈹?  鈹溾攢鈹€ payment-core 鈫?inventory-service
  鈹?  鈹斺攢鈹€ 鐩村埌閾捐矾缁堢偣
  鈹?  鈻?閾捐矾缁堢偣
  鈹?鈶?娓呯悊
  鈹?  鈹溾攢鈹€ GatewayFilter.doFinally 鈫?GrayContext.clear()
  鈹?  鈹斺攢鈹€ 姣忎釜 Provider Filter.finally 鈫?GrayContext.clear()
```

> **鑱岃矗杈圭晫璇存槑锛欸ateway 灞備笌 Dubbo 灞傜殑璺敱鍒嗗伐**
>
> ADR-029 涓畾涔夌殑 `VersionRouteFilter`锛圙ateway 灞傦級涓庢湰 ADR 鐨?`GrayTagRouter`锛圖ubbo 灞傦級鎵挎媴涓嶅悓鑱岃矗锛岄伩鍏嶄袱灞傚仛閲嶅鐨勮矾鐢卞喅绛栵細
>
> - **VersionRouteFilter锛圙ateway 灞傦級**锛氫粎璐熻矗鐏板害璇锋眰鐨?*璇嗗埆涓庢爣璁?*銆傞€氳繃 Header銆佺櫧鍚嶅崟銆佹瘮渚嬬瓑绛栫暐鍒ゅ畾璇锋眰鏄惁灞炰簬鐏板害娴侀噺锛岀敓鎴?`GrayTag` 骞跺啓鍏?`GrayContext`銆?*涓嶈礋璐ｆ湇鍔″疄渚嬬骇鍒殑鐗堟湰閫夋嫨**銆?> - **GrayTagRouter锛圖ubbo 灞傦級**锛氳礋璐?*鏈嶅姟瀹炰緥鐨勭増鏈€夋嫨**銆傛牴鎹?`GrayContext` 涓殑 `grayTag`锛屾煡璇?Apollo 瑙勫垯涓殑鐗堟湰鏄犲皠锛坄tagToVersionMapping`锛夛紝绛涢€?Nacos 涓?`version` metadata 鍖归厤鐨勭伆搴﹀疄渚嬭繘琛岃矾鐢便€?>
> 杩欎竴鍒嗗眰璁捐纭繚 Gateway 灞備笓娉ㄦ祦閲忔爣璁颁笌涓婁笅鏂囨敞鍏ワ紝瀹炰緥璺敱瀹屽叏鐢?Dubbo 灞傜殑鍘熺敓 SPI Router 鏈哄埗瀹屾垚锛屼袱灞傚悇鍙稿叾鑱屻€佷簰涓嶅共鎵般€?
### 2. 鏍稿績缁勪欢

#### 2.1 GrayContext + GrayTag锛堢伆搴︿笂涓嬫枃涓庢爣绛撅級

```java
/**
 * 鐏板害涓婁笅鏂?鈥?璇锋眰绾у埆鐨勭伆搴︽爣绛炬寔鏈夎€? *
 * 鐢熷懡鍛ㄦ湡锛氳姹傚叆鍙ｏ紙Gateway/Provider Filter锛夆啋 璇锋眰缁撴潫锛團ilter finally锛? * 浼犻€掓柟寮忥細Dubbo RpcContext锛堣法鏈嶅姟锛夆啋 GrayContext锛堟湇鍔″唴 ThreadLocal锛? */
public class GrayContext {

    private static final NamedThreadLocal<GrayTag> GRAY_TAG_HOLDER =
            new NamedThreadLocal<>("grayTagContext");

    public static void set(GrayTag tag) {
        GRAY_TAG_HOLDER.set(tag);
        MDC.put("grayTag", tag != null ? tag.getGrayTag() : "");
    }

    public static GrayTag get() {
        return GRAY_TAG_HOLDER.get();
    }

    public static boolean isGray() {
        return GRAY_TAG_HOLDER.get() != null;
    }

    public static String getGrayVersion() {
        GrayTag tag = GRAY_TAG_HOLDER.get();
        return tag != null ? tag.getGrayTag() : null;
    }

    /** 蹇呴』鍦?finally 鍧椾腑璋冪敤 */
    public static void clear() {
        GRAY_TAG_HOLDER.remove();
        MDC.remove("grayTag");
    }
}

/**
 * 鐏板害鏍囩鍊煎璞? */
@Data
@Builder
public class GrayTag {
    private String grayTag;                    // 鐏板害鐗堟湰鏍囪瘑 "v2.3.0-canary"
    private String grayStrategy;               // 鐏板害绛栫暐 "user-whitelist"
    private String traceId;                    // SkyWalking TraceId
    private long timestamp;                    // 鏍囩鐢熸垚鏃堕棿
    private Map<String, String> attributes;    // 鎵╁睍灞炴€?}
```

#### 2.2 GatewayGrayFilter锛堢伆搴﹀叆鍙ｈ繃婊ゅ櫒锛?
```java
/**
 * 缃戝叧鐏板害杩囨护鍣?鈥?鍏ㄩ摼璺伆搴︾殑鍏ュ彛
 *
 * 鑱岃矗锛? * 1. 璇嗗埆鐏板害璇锋眰锛坔eader / 鐧藉悕鍗?/ 姣斾緥 / 瑙勫垯鍖归厤锛? * 2. 鐢熸垚 GrayTag 骞跺啓鍏?RpcContext
 * 3. 鍝嶅簲澶翠腑閫忓嚭鐏板害鏍囪瘑锛圖ebug 鐢級
 *
 * order = -1000锛堟渶楂樹紭鍏堢骇锛屽湪璺敱涔嬪墠鎵ц锛? */
@Component
@Order(-1000)
public class GatewayGrayFilter implements GlobalFilter {

    @Autowired
    private GrayRoutingRuleService routingRuleService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GrayTag grayTag = determineGrayTag(exchange);
        if (grayTag == null) {
            return chain.filter(exchange);
        }

        GrayContext.set(grayTag);

        // 鍐欏叆璇锋眰澶达紙涓嬫父 HTTP 鏈嶅姟鍙劅鐭ワ級
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header("x-gray-tag", grayTag.getGrayTag())
                .build();

        // 鍐欏叆 Dubbo RpcContext锛圖ubbo 鏈嶅姟閫氳繃 Consumer Filter 浼犻€掞級
        RpcContext.getClientAttachment().setAttachment("grayTag",
                JsonUtils.toJson(grayTag));

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(request)
                .response(response -> {
                    response.getHeaders().add("x-gray-result",
                            "canary:" + grayTag.getGrayTag());
                })
                .build();

        return chain.filter(mutatedExchange)
                .doFinally(signalType -> GrayContext.clear());
    }

    /**
     * 鐏板害鍒ゅ畾锛堢煭璺€昏緫锛氬懡涓换涓€鏉″嵆杩斿洖 GrayTag锛?     */
    private GrayTag determineGrayTag(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        // Step 1: 璇锋眰澶村己鍒舵爣绛撅紙鏈€楂樹紭鍏堢骇锛屾祴璇?棰勫彂浣跨敤锛?        String forceTag = request.getHeaders().getFirst("x-gray-tag");
        if (StringUtils.hasText(forceTag) && forceTag.startsWith("force-")) {
            return GrayTag.builder()
                    .grayTag(forceTag.replace("force-", ""))
                    .grayStrategy("force-header")
                    .traceId(getTraceId())
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        GrayRoutingRule rule = routingRuleService.getCurrentRule();
        if (rule == null) return null;

        // Step 2: 鐢ㄦ埛鐧藉悕鍗?        String userId = extractUserId(request);
        if (userId != null && rule.getUserWhitelist() != null
                && rule.getUserWhitelist().contains(userId)) {
            return GrayTag.builder()
                    .grayTag(rule.getGrayVersion())
                    .grayStrategy("user-whitelist")
                    .traceId(getTraceId())
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        // Step 3: 鎸夋瘮渚嬬伆搴︼紙hash userId 淇濇寔纭畾鎬э級
        if (userId != null && rule.getGrayPercentage() > 0) {
            int hash = Math.abs(userId.hashCode()) % 100;
            if (hash < rule.getGrayPercentage()) {
                return GrayTag.builder()
                        .grayTag(rule.getGrayVersion())
                        .grayStrategy("percentage:" + rule.getGrayPercentage())
                        .traceId(getTraceId())
                        .timestamp(System.currentTimeMillis())
                        .build();
            }
        }

        // Step 4: Header/Path 鍖归厤
        if (matchesHeaderRule(request, rule)) {
            return GrayTag.builder()
                    .grayTag(rule.getGrayVersion())
                    .grayStrategy("header-match")
                    .traceId(getTraceId())
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        return null; // 闈炵伆搴﹁姹?    }

    private String extractUserId(ServerHttpRequest request) {
        // 浠?JWT Token / Header / Cookie 涓彁鍙栫敤鎴?ID
        return request.getHeaders().getFirst("x-user-id");
    }

    private boolean matchesHeaderRule(ServerHttpRequest request, GrayRoutingRule rule) {
        if (rule.getHeaderRules() == null) return false;
        return rule.getHeaderRules().entrySet().stream()
                .anyMatch(entry -> {
                    String headerValue = request.getHeaders().getFirst(entry.getKey());
                    return headerValue != null && entry.getValue().equals(headerValue);
                });
    }

    private String getTraceId() {
        return Optional.ofNullable(RequestContext.getTraceId())
                .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
    }
}
```

#### 2.3 GrayTagConsumerFilter锛圖ubbo 娑堣垂绔繃婊ゅ櫒锛?
```java
/**
 * Dubbo 娑堣垂绔繃婊ゅ櫒 鈥?浼犻€掔伆搴︽爣绛惧埌涓嬫父鏈嶅姟
 *
 * SPI 閰嶇疆锛歁ETA-INF/dubbo/org.apache.dubbo.rpc.Filter
 *   grayTagConsumer=com.omplatform.gray.filter.GrayTagConsumerFilter
 *
 * 鍏ㄥ眬鍔犺浇锛歞ubbo.consumer.filter=grayTagConsumer,default
 */
@Activate(group = {CommonConstants.CONSUMER}, order = -8000)
public class GrayTagConsumerFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        GrayTag tag = GrayContext.get();

        if (tag != null) {
            // 灏?grayTag 鍐欏叆 RpcContext锛岄殣寮忎紶閫掑埌 Provider 绔?            RpcContext.getClientAttachment().setAttachment("grayTag",
                    JsonUtils.toJson(tag));
            // 鍏煎 Dubbo 2.7+ 鐨?attachments 鏈哄埗
            invocation.getObjectAttachments().put("grayTag",
                    JsonUtils.toJson(tag));
        }

        Result result = invoker.invoke(invocation);

        // 浠庡搷搴斾腑璇诲彇鏈嶅姟绔増鏈俊鎭紝璁板綍鐗堟湰涓€鑷存€?        String serverVersion = result.getAttachment("serverGrayVersion");
        if (tag != null && serverVersion != null) {
            MetricCollector.recordVersionConsistency(
                    tag.getGrayTag(), serverVersion,
                    invocation.getTargetServiceUniqueName());
        }

        return result;
    }
}
```

#### 2.4 GrayTagProviderFilter锛圖ubbo 鎻愪緵绔繃婊ゅ櫒锛?
```java
/**
 * Dubbo 鎻愪緵绔繃婊ゅ櫒 鈥?鎺ユ敹鐏板害鏍囩骞跺缓绔嬬伆搴︿笂涓嬫枃
 *
 * SPI 閰嶇疆锛歁ETA-INF/dubbo/org.apache.dubbo.rpc.Filter
 *   grayTagProvider=com.omplatform.gray.filter.GrayTagProviderFilter
 *
 * 鍏ㄥ眬鍔犺浇锛歞ubbo.provider.filter=grayTagProvider,default
 */
@Activate(group = {CommonConstants.PROVIDER}, order = -8000)
public class GrayTagProviderFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String grayTagJson = RpcContext.getServerAttachment().getAttachment("grayTag");

        if (grayTagJson == null) {
            return invoker.invoke(invocation);
        }

        GrayTag grayTag = JsonUtils.fromJson(grayTagJson, GrayTag.class);
        GrayContext.set(grayTag);

        try {
            Result result = invoker.invoke(invocation);

            // 鍦ㄥ搷搴斾腑闄勫姞鏈湇鍔＄殑鐗堟湰淇℃伅锛堜緵娑堣垂绔牎楠屼竴鑷存€э級
            result.setAttachment("serverGrayVersion",
                    AppVersionHolder.getCurrentVersion());

            return result;
        } finally {
            GrayContext.clear();
        }
    }
}
```

#### 2.5 GrayTagRouter锛圖ubbo 鐏板害璺敱鍣級

```java
/**
 * 鐏板害鏍囩璺敱鍣?鈥?鏍规嵁 GrayTag 璺敱鍒板搴旂増鏈殑鏈嶅姟瀹炰緥
 *
 * SPI 閰嶇疆锛歁ETA-INF/dubbo/org.apache.dubbo.rpc.cluster.RouterFactory
 *   grayTagRouter=com.omplatform.gray.router.GrayTagRouterFactory
 *
 * 璺敱閫昏緫锛? *   1. 鏃?GrayTag 鈫?璧伴粯璁よ矾鐢憋紙绋冲畾鐗堟湰锛? *   2. 鏈?GrayTag 鈫?鏌ヨ Apollo GrayRoutingRule
 *   3. 鍖归厤 grayVersion 鈫?绛涢€?Nacos metadata 涓?version 鍖归厤鐨勫疄渚? *   4. 鏃犲尮閰嶇伆搴﹀疄渚?鈫?fallback 鍒扮ǔ瀹氱増鏈紙闄嶇骇锛? *   5. 璁板綍璺敱鍐崇瓥鍒?Metrics
 */
@Activate(order = -9000)
public class GrayTagRouter implements Router {

    @Override
    public <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url,
                                       Invocation invocation) throws RpcException {
        GrayTag tag = GrayContext.get();
        if (tag == null || tag.getGrayTag() == null) {
            return invokers;
        }

        GrayRoutingRule rule = GrayRoutingRuleService.getCurrentRule();
        String targetVersion = rule.getTagToVersionMapping().get(tag.getGrayTag());
        if (targetVersion == null) {
            MetricCollector.incrementGrayRouteFallback(tag.getGrayTag());
            return invokers;
        }

        List<Invoker<T>> grayInvokers = invokers.stream()
                .filter(invoker -> {
                    String version = invoker.getUrl().getParameter("version");
                    return targetVersion.equals(version);
                })
                .collect(Collectors.toList());

        if (grayInvokers.isEmpty()) {
            logger.warn("鐏板害鐗堟湰 {} 鏃犲彲鐢ㄥ疄渚? 闄嶇骇鍒扮ǔ瀹氱増鏈? targetVersion={}",
                    tag.getGrayTag(), targetVersion);
            MetricCollector.incrementGrayRouteDegradation(
                    tag.getGrayTag(), targetVersion);
            return invokers; // 鍏ㄩ儴杩斿洖锛堢ǔ瀹氱増鏈級
        }

        MetricCollector.incrementGrayRouteSuccess(tag.getGrayTag(), targetVersion);
        return grayInvokers;
    }
}

/**
 * GrayTagRouterFactory 鈥?SPI 宸ュ巶绫? */
public class GrayTagRouterFactory implements RouterFactory {
    @Override
    public Router getRouter(URL url) {
        return new GrayTagRouter();
    }
}
```

#### 2.6 GrayRoutingRule锛圓pollo 閰嶇疆妯″瀷锛?
```java
/**
 * 鐏板害璺敱瑙勫垯 鈥?瀛樺偍鍦?Apollo Namespace: gray-routing-rule锛屽疄鏃剁敓鏁? *
 * Apollo 閰嶇疆鏍煎紡 (JSON):
 * {
 *   "grayRule": {
 *     "grayVersion": "v2.3.0-canary",
 *     "grayPercentage": 5,
 *     "userWhitelist": ["u10001", "u10002", "u10003", "u10004", "u10005"],
 *     "headerRules": { "x-gray-source": "internal-test" },
 *     "tagToVersionMapping": { "v2.3.0-canary": "2.3.0" },
 *     "serviceGrayList": ["order-core", "payment-core", "inventory-service"],
 *     "compatibilityCheck": { "enabled": true, "minStableVersion": "2.2.0" },
 *     "dataIsolation": {
 *       "enabled": true,
 *       "dbSuffix": "_canary",
 *       "mqTopicPrefix": "canary_",
 *       "redisKeyPrefix": "canary:"
 *     }
 *   }
 * }
 */
@Data
public class GrayRoutingRule {
    /** 褰撳墠鐏板害鐗堟湰鏍囪瘑 */
    private String grayVersion;
    /** 鐏板害姣斾緥锛?-100锛?*/
    private int grayPercentage;
    /** 鐢ㄦ埛鐧藉悕鍗?*/
    private List<String> userWhitelist;
    /** Header 鍖归厤瑙勫垯 */
    private Map<String, String> headerRules;
    /** 璺緞鍖归厤瑙勫垯 */
    private List<String> pathRules;
    /** tag 鈫?鐗堟湰鍙锋槧灏?*/
    private Map<String, String> tagToVersionMapping;
    /** 鍙備笌鐏板害鐨勬湇鍔″垪琛紙绌?= 鍏ㄩ摼璺伆搴︼級 */
    private List<String> serviceGrayList;
    /** 鍏煎鎬ф鏌ラ厤缃?*/
    private CompatibilityConfig compatibilityCheck;
    /** 鏁版嵁闅旂閰嶇疆 */
    private DataIsolationConfig dataIsolation;
}

/**
 * Apollo 閰嶇疆鐩戝惉鏈嶅姟
 */
@Component
public class GrayRoutingRuleService {

    private static volatile GrayRoutingRule currentRule = new GrayRoutingRule();

    @PostConstruct
    public void init() {
        Config config = ConfigService.getConfig("gray-routing-rule");
        config.addChangeListener(changeEvent -> refreshRule());
        refreshRule();
    }

    private void refreshRule() {
        String json = ConfigService.getConfig("gray-routing-rule")
                .getProperty("grayRule", "{}");
        GrayRoutingRule newRule = JsonUtils.fromJson(json, GrayRoutingRule.class);
        if (newRule != null) {
            currentRule = newRule;
            logger.info("鐏板害璺敱瑙勫垯宸叉洿鏂? version={}, percentage={}",
                    newRule.getGrayVersion(), newRule.getGrayPercentage());
        }
    }

    public static GrayRoutingRule getCurrentRule() {
        return currentRule;
    }
}
```

#### 2.7 VersionCompatibilityChecker锛堢増鏈吋瀹规€ф鏌ワ級

```java
/**
 * 鐗堟湰鍏煎鎬ф鏌?鈥?闃叉鐏板害鐗堟湰璋冪敤涓嶅吋瀹圭殑绋冲畾鐗堟湇鍔? *
 * 鍘熺悊锛氭瘡涓湇鍔″湪 Nacos metadata 涓敞鍐岃嚜宸辩殑鑳藉姏鐗瑰緛
 * 鐏板害鐗堟湰璋冪敤涓嬫父鏃讹紝妫€鏌ヤ笅娓哥増鏈槸鍚︽弧瓒冲吋瀹规€ц姹? *
 * Nacos metadata 绀轰緥锛? * {
 *   "version": "2.3.0",
 *   "features": ["checkout_v2", "payment_new_flow"],
 *   "requires": ">=2.0.0"
 * }
 */
@Component
public class VersionCompatibilityChecker {

    /**
     * 妫€鏌ョ伆搴︾増鏈槸鍚﹁兘璋冪敤鐩爣鏈嶅姟瀹炰緥
     *
     * @param grayVersion    褰撳墠鐏板害鐗堟湰
     * @param targetMetadata 鐩爣鏈嶅姟 Nacos metadata
     * @return CheckResult
     */
    public CheckResult check(String grayVersion, Map<String, String> targetMetadata) {
        String requiredMinVersion = targetMetadata.get("requires");
        if (requiredMinVersion == null) {
            return CheckResult.compatible();
        }

        Version gray = Version.parse(grayVersion);
        Version required = Version.parse(requiredMinVersion);

        if (gray.compareTo(required) < 0) {
            return CheckResult.incompatible(
                String.format("鐏板害鐗堟湰 %s < 鐩爣鏈嶅姟鏈€浣庤姹?%s",
                    grayVersion, requiredMinVersion));
        }

        // 妫€鏌ョ伆搴︾増鏈槸鍚﹁皟鐢ㄤ簡宸插簾寮冪殑鎺ュ彛
        String deprecatedFeatures = targetMetadata.get("deprecatedFeatures");
        if (deprecatedFeatures != null) {
            List<String> deprecated = JsonUtils.fromJsonList(deprecatedFeatures, String.class);
            return CheckResult.compatibleWithWarning(
                "鐏板害鐗堟湰渚濊禆宸插簾寮冨姛鑳? " + deprecated);
        }

        return CheckResult.compatible();
    }
}
```

### 3. 鐏板害鍒ゅ畾娴佺▼锛堣缁嗭級

```
鐏板害鍒ゅ畾娴佺▼锛堢煭璺€昏緫 鈥?浠庝笂鍒颁笅锛屽懡涓嵆杩斿洖锛?鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
璇锋眰杩涘叆 Gateway
  鈹?  鈹溾攢鈹€ 1. 璇锋眰澶村己鍒舵爣绛撅紙鏈€楂樹紭鍏堢骇锛?  鈹?   x-gray-tag: force-v2.3.0      鈫?寮哄埗浠?v2.3.0 鐏板害锛堟祴璇?棰勫彂浣跨敤锛?  鈹?   x-gray-tag: force-stable      鈫?寮哄埗璧扮ǔ瀹氱増锛堟祴璇?棰勫彂浣跨敤锛?  鈹?   鈹斺攢鈹€ 鍛戒腑 鈫?杩斿洖 GrayTag(grayTag="v2.3.0-canary", strategy="force-header")
  鈹?  鈹溾攢鈹€ 2. 鐢ㄦ埛鐧藉悕鍗?  鈹?   GrayRoutingRule.userWhitelist = ["u10001", "u10002", ...]
  鈹?   userId from JWT/Header/Cookie
  鈹?   鈹斺攢鈹€ 鍛戒腑 鈫?杩斿洖 GrayTag(grayTag=rule.grayVersion, strategy="user-whitelist")
  鈹?  鈹溾攢鈹€ 3. 鎸夋瘮渚嬬伆搴?  鈹?   GrayRoutingRule.grayPercentage = 5
  鈹?   hash(userId) % 100 < 5
  鈹?   鈹斺攢鈹€ 鍛戒腑 鈫?杩斿洖 GrayTag(grayTag=rule.grayVersion, strategy="percentage:5")
  鈹?  鈹溾攢鈹€ 4. Header/Path 鍖归厤
  鈹?   Header: x-gray-source = "internal-test"
  鈹?   Path:  /api/v2/checkout
  鈹?   鈹斺攢鈹€ 鍛戒腑 鈫?杩斿洖 GrayTag(grayTag=rule.grayVersion, strategy="header-match")
  鈹?  鈹斺攢鈹€ 5. 榛樿 鈫?闈炵伆搴﹁姹傦紙杩斿洖 null锛?```

### 4. 鐏板害璺敱娴侊紙瀹屾暣 ASCII 鍥撅級

```
鍏ㄩ摼璺伆搴﹁矾鐢辨祦
鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
                           鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                           鈹?  External    鈹?                           鈹?  Request     鈹?                           鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹?                                  鈹?                                  鈻?                    鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                    鈹?  Spring Cloud Gateway   鈹?                    鈹?  GatewayGrayFilter      鈹?                    鈹?  鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹?                    鈹?  鈹?鐏板害鍒ゅ畾 鈫?GrayTag 鈹? 鈹?                    鈹?  鈹?RpcContext 鍐欏叆    鈹? 鈹?                    鈹?  鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹? 鈹?                    鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                               鈹?              鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?              鈹?               鈹?               鈹?              鈻?               鈻?               鈻?     鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?     鈹?x-user-id:     鈹?鈹?x-gray-  鈹?鈹?鏃犵伆搴︽爣绛?     鈹?     鈹?10001 (鐧藉悕鍗?  鈹?鈹?tag:     鈹?鈹?(榛樿)         鈹?     鈹?               鈹?鈹?force-   鈹?鈹?               鈹?     鈹?鈫?鐏板害鏍囩      鈹?鈹?v2.3.0   鈹?鈹?鈫?鏃犵伆搴︽爣绛?   鈹?     鈹?  v2.3.0-canary鈹?鈹?         鈹?鈹?               鈹?     鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?             鈹?              鈹?              鈹?             鈻?              鈻?              鈻?   鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?   鈹?Dubbo 鈫?order-  鈹?鈹?Dubbo 鈫?     鈹?鈹?Dubbo 鈫?order-  鈹?   鈹?core            鈹?鈹?order-core   鈹?鈹?core            鈹?   鈹?GrayTagConsumer-鈹?鈹?(鍚屼笂)       鈹?鈹?(鏃?GrayTag)    鈹?   鈹?Filter 浼犻€?tag  鈹?鈹?             鈹?鈹?                鈹?   鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?            鈹?                鈹?                 鈹?            鈻?                鈻?                 鈻?   鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?   鈹?GrayTagProvider 鈹?鈹?Provider     鈹?鈹?Provider Filter 鈹?   鈹?Filter          鈹?鈹?Filter (鍚屼笂) 鈹?鈹?(鏃?tag, 璺宠繃)  鈹?   鈹?寤虹珛 GrayContext 鈹?鈹?             鈹?鈹?                 鈹?   鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?            鈹?                鈹?                 鈹?            鈻?                鈻?                 鈻?   鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?   鈹?GrayTagRouter    鈹?鈹?GrayTagRouter鈹?鈹?Router (鏃?tag) 鈹?   鈹?version="2.3.0"  鈹?鈹?(鍚屼笂)       鈹?鈹?鈫?绋冲畾鐗堣矾鐢?    鈹?   鈹?鈫?璺敱鍒扮伆搴﹀疄渚? 鈹?鈹?             鈹?鈹?                 鈹?   鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?            鈹?                鈹?                 鈹?            鈻?                鈻?                 鈻?   鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?   鈹?order-core      鈹?鈹?order-core   鈹?鈹?order-core      鈹?   鈹?version: 2.3.0  鈹?鈹?version:2.3.0鈹?鈹?version: 2.2.0  鈹?   鈹?gray=true       鈹?鈹?gray=true    鈹?鈹?(绋冲畾鐗?         鈹?   鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?            鈹?                鈹?            鈹?Dubbo Call      鈹?Dubbo Call
            鈹?(GrayTag 鎸佺画   鈹?(GrayTag 鎸佺画浼犻€?
            鈹?浼犻€?           鈹?            鈻?                鈻?   鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?   鈹?payment-core     鈹?鈹?payment-core     鈹?   鈹?version: 2.3.0   鈹?鈹?version: 2.3.0   鈹?   鈹?gray=true        鈹?鈹?gray=true        鈹?   鈹?(Provider Filter 鈹?鈹?(鍚屼笂)           鈹?   鈹?鈫?GrayTagRouter) 鈹?鈹?                 鈹?   鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?            鈹?                   鈹?            鈹?Dubbo              鈹?Dubbo
            鈻?                   鈻?   鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?   鈹?inventory-service鈹?鈹?inventory-service鈹?   鈹?version: 2.3.0   鈹?鈹?version: 2.3.0   鈹?   鈹?gray=true        鈹?鈹?gray=true        鈹?   鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?
鈺愨晲鈺?鍏抽敭鍘熷垯 鈺愨晲鈺?   馃煛 鐏板害璇锋眰 鈫?鍏ㄩ摼璺矾鐢卞埌鐏板害鐗堟湰 (v2.3.0)
   馃數 绋冲畾璇锋眰 鈫?鍏ㄩ摼璺矾鐢卞埌绋冲畾鐗堟湰 (v2.2.0)
   馃煝 鐏板害鍜岀ǔ瀹氭祦閲忓湪鍚勮嚜鐗堟湰鐨勫疄渚嬩腑杩愯锛屼簰涓嶅共鎵?```

### 5. Apollo 瑙勫垯閰嶇疆

#### 5.1 閰嶇疆妯℃澘

```json
{
  "grayRule": {
    "grayVersion": "v2.3.0-canary",
    "grayPercentage": 5,
    "userWhitelist": [
      "u10001", "u10002", "u10003", "u10004"
    ],
    "headerRules": {
      "x-gray-source": "internal-test",
      "x-gray-region": "shanghai"
    },
    "pathRules": [
      "/api/v2/checkout",
      "/api/v2/payment/new-flow"
    ],
    "tagToVersionMapping": {
      "v2.3.0-canary": "2.3.0",
      "v2.4.0-canary": "2.4.0"
    },
    "serviceGrayList": [
      "order-core",
      "payment-core",
      "inventory-service"
    ],
    "compatibilityCheck": {
      "enabled": true,
      "minStableVersion": "2.2.0",
      "strictMode": false
    },
    "dataIsolation": {
      "enabled": true,
      "dbSuffix": "_canary",
      "mqTopicPrefix": "canary_",
      "redisKeyPrefix": "canary:"
    },
    "routeFallback": {
      "strategy": "to-stable",
      "warnWhenNoGrayInstance": true
    },
    "monitoring": {
      "sampleRate": 100,
      "logGrayRequest": true,
      "alertOnGrayErrorSpike": true,
      "grayErrorThresholdPercent": 1.0
    }
  }
}
```

#### 5.2 鍦烘櫙鍖栭厤缃ず渚?
```json
// 鍦烘櫙 1: 鐢ㄦ埛鐧藉悕鍗曠伆搴︼紙5 涓唴閮ㄦ祴璇曠敤鎴凤紝鍏ㄩ摼璺級
{
  "grayVersion": "v2.3.0-canary",
  "grayPercentage": 0,
  "userWhitelist": ["u10001", "u10002", "u10003", "u10004", "u10005"],
  "tagToVersionMapping": { "v2.3.0-canary": "2.3.0" },
  "serviceGrayList": ["order-core", "payment-core", "inventory-service"]
}

// 鍦烘櫙 2: 鎸夋瘮渚嬬伆搴︼紙5% 鐢ㄦ埛閫愭鏀鹃噺锛?{
  "grayVersion": "v2.3.0-canary",
  "grayPercentage": 5,
  "userWhitelist": [],
  "tagToVersionMapping": { "v2.3.0-canary": "2.3.0" },
  "serviceGrayList": ["order-core", "payment-core", "inventory-service"]
}

// 鍦烘櫙 3: 鍐呴儴娓犻亾鐏板害锛堜粎鍐呴儴 APP 鐗堟湰锛?{
  "grayVersion": "v2.3.0-canary",
  "grayPercentage": 0,
  "headerRules": { "x-app-version": "7.5.0-internal" },
  "tagToVersionMapping": { "v2.3.0-canary": "2.3.0" }
}

// 鍦烘櫙 4: 鍗曚竴鏈嶅姟鐏板害锛堜粎 order-core锛屽叾浠栨湇鍔¤蛋绋冲畾鐗堬級
{
  "grayVersion": "v2.3.0-order-only",
  "grayPercentage": 5,
  "tagToVersionMapping": { "v2.3.0-order-only": "2.3.0" },
  "serviceGrayList": ["order-core"]
}
```

### 6. 澶氭湇鍔＄伆搴︾紪鎺掓祦绋?
```
澶氭湇鍔＄伆搴︾紪鎺掓祦绋嬶紙浠ユ柊缁撶畻娴佺▼ v2.3.0 涓轰緥锛?鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
Phase 1 鈥?鍐呴儴楠岃瘉锛圖ay 1, 鎸佺画 1h锛?  鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?  鈹?鐩爣锛氶獙璇佺伆搴?tag 鍩虹閾捐矾閫氶『                                  鈹?  鈹?                                                              鈹?  鈹?鎿嶄綔锛?                                                         鈹?  鈹?鈶?閮ㄧ讲 v2.3.0 鍒?3 涓湇鍔＄殑鐏板害鐜锛堝悇 1 Pod锛?                   鈹?  鈹?鈶?Apollo 閰嶇疆锛歶serWhitelist=[娴嬭瘯璐﹀彿]锛実rayPercentage=0         鈹?  鈹?鈶?GatewayGrayFilter 楠岃瘉锛歺-gray-tag header 鈫?鍏ㄩ摼璺伆搴?         鈹?  鈹?                                                              鈹?  鈹?楠岃瘉锛?                                                         鈹?  鈹?鈻?鐏板害璇锋眰浠?Gateway 鈫?order-core 鈫?payment 鈫?inventory 鍏ㄩ摼璺?  鈹?  鈹?鈻?SkyWalking 涓兘鐪嬪埌 grayTag tag                               鈹?  鈹?鈻?鐏板害瀹炰緥鏃ュ織甯?[gray=v2.3.0-canary] 鏍囪                        鈹?  鈹?鈻?绋冲畾娴侀噺涓嶈繘鍏ョ伆搴﹀疄渚?                                          鈹?  鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?
Phase 2 鈥?1% 鐏板害锛圖ay 2, 瑙傚療 30min锛?  鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?  鈹?鐩爣锛氱湡瀹炵敤鎴?1% 娴侀噺楠岃瘉鍏ㄩ摼璺?                                   鈹?  鈹?                                                              鈹?  鈹?鎿嶄綔锛欰pollo 鏇存柊 grayPercentage=1                               鈹?  鈹?                                                              鈹?  鈹?鐩戞帶鎸囨爣锛?                                                      鈹?  鈹?鈻?GrayExceptionRatio < 0.1%                                     鈹?  鈹?鈻?GrayP99 < 绋冲畾鐗?P99 + 20ms                                    鈹?  鈹?鈻?GrayFallbackCount = 0锛堟棤鐏板害闄嶇骇鍒扮ǔ瀹氱増锛?                      鈹?  鈹?鈻?3 涓湇鍔＄伆搴︾増鏈疄渚嬬殑 CPU/鍐呭瓨/GC 姝ｅ父                           鈹?  鈹?                                                              鈹?  鈹?鍥炴粴鏉′欢锛氫互涓婁换涓€椤逛笉杈炬爣 鈫?Apollo grayPercentage=0               鈹?  鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?
Phase 3 鈥?5% 灏忚寖鍥达紙Day 3, 瑙傚療 60min锛?  鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?  鈹?鎿嶄綔锛欰pollo grayPercentage=5                                   鈹?  鈹?                                                              鈹?  鈹?棰濆楠岃瘉锛?                                                      鈹?  鈹?鈻?鐏板害璇锋眰瑙﹁揪 payment-service 鍜?inventory-service 鐨勭伆搴﹀疄渚?    鈹?  鈹?鈻?鐏板害鏁版嵁涓庣ǔ瀹氭暟鎹殧绂绘甯革紙DB 鍚庣紑/Redis 鍓嶇紑/MQ Topic锛?       鈹?  鈹?鈻?鏃犵伆搴︾増鏈殑鍏煎鎬ф姇璇夛紙绋冲畾 order-core 璋冪敤 gray payment?锛?    鈹?  鈹?                                                              鈹?  鈹?鍥炴粴鏉′欢锛氱伆搴︽暟鎹殧绂诲紓甯告垨鍏煎鎬ч棶棰?鈫?Apollo 鍥炴粴               鈹?  鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?
Phase 4 鈥?20% 鎵╁ぇ锛圖ay 4, 瑙傚療 120min锛?  鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?  鈹?鎿嶄綔锛欰pollo grayPercentage=20                                  鈹?  鈹?                                                              鈹?  鈹?鍘嬪姏楠岃瘉锛?                                                      鈹?  鈹?鈻?鐏板害瀹炰緥杩炴帴姹?DB 杩炴帴鏁版甯?                                    鈹?  鈹?鈻?缂撳瓨鍛戒腑鐜囨棤鏄庢樉涓嬮檷                                             鈹?  鈹?鈻?鐏板害涓庣ǔ瀹氱増涔嬮棿 MQ 娑堟伅娑堣垂姝ｅ父                                  鈹?  鈹?                                                              鈹?  鈹?鍥炴粴鏉′欢锛氳祫婧愪娇鐢ㄥ紓甯告垨娑堟伅澶勭悊寮傚父 鈫?Apollo 鍥炴粴                  鈹?  鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?
Phase 5 鈥?50% 杩囧崐锛圖ay 5, 瑙傚療 240min锛?  鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?  鈹?鎿嶄綔锛欰pollo grayPercentage=50                                  鈹?  鈹?                                                              鈹?  鈹?妫€鏌ワ細鍏ㄩ噺鐩戞帶鎸囨爣锛堜笟鍔℃垚鍔熺巼銆佽祫閲戝璐︺€佹暟鎹竴鑷存€э級                  鈹?  鈹?                                                              鈹?  鈹?鍥炴粴鏉′欢锛氳祫閲戝璐︿笉涓€鑷存垨鏁版嵁涓€鑷存€ч棶棰?鈫?Apollo 鍥炴粴              鈹?  鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?
Phase 6 鈥?100% 鍏ㄩ噺锛圖ay 6锛?  鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?  鈹?鎿嶄綔锛?                                                        鈹?  鈹?鈶?grayPercentage=100锛堟垨绉婚櫎鐏板害瑙勫垯锛屽叏閮ㄨ蛋鏂扮増鏈級               鈹?  鈹?鈶?鐏板害 Pod 淇濈暀 15 鍒嗛挓瑙傚療 鈫?缂╁                                鈹?  鈹?鈶?娓呯悊 Apollo 鐏板害閰嶇疆                                           鈹?  鈹?鈶?鏇存柊鐩戞帶鍩虹嚎                                                    鈹?  鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?```

### 7. 閮ㄧ讲鏂规

#### 7.1 Dubbo SPI 閰嶇疆

```
# META-INF/dubbo/org.apache.dubbo.rpc.Filter
grayTagConsumer=com.omplatform.gray.filter.GrayTagConsumerFilter
grayTagProvider=com.omplatform.gray.filter.GrayTagProviderFilter

# META-INF/dubbo/org.apache.dubbo.rpc.cluster.RouterFactory
grayTagRouter=com.omplatform.gray.router.GrayTagRouterFactory
```

#### 7.2 鏈嶅姟 Dubbo 閰嶇疆

```yaml
# application.yml 鈥?姣忎釜鏈嶅姟鍔犺浇鐏板害 Filter 鍜?Router
dubbo:
  application:
    name: order-core
    version: "2.3.0"                    # 鏈嶅姟鐗堟湰锛孯outer 鍖归厤鐢?  provider:
    filter: "grayTagProvider,default"   # 鍔犺浇 Provider Filter
  consumer:
    filter: "grayTagConsumer,default"   # 鍔犺浇 Consumer Filter
    router: "grayTagRouter"             # 鍚敤鐏板害 Router
```

#### 7.3 Nacos 鏈嶅姟娉ㄥ唽 metadata

```yaml
# bootstrap.yml 鈥?姣忎釜鏈嶅姟閰嶇疆鑷韩鐗堟湰淇℃伅
spring:
  application:
    name: order-core
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER:nacos-cluster:8848}
        metadata:
          version: "${APP_VERSION:2.2.0}"
          gray: "${GRAY_ENABLED:false}"
          grayVersion: "${GRAY_VERSION:}"
          requires: ">=2.0.0"
          features: '["checkout_v2", "payment_new_flow"]'
```

#### 7.4 K8s 鐏板害 Pod Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-core-canary
  labels:
    app: order-core
    version: "2.3.0"
    gray: "true"
spec:
  replicas: 2
  selector:
    matchLabels:
      app: order-core
      version: "2.3.0"
  template:
    metadata:
      labels:
        app: order-core
        version: "2.3.0"
        gray: "true"
    spec:
      containers:
      - name: order-core
        image: omplatform/order-core:2.3.0-canary
        env:
        - name: APP_VERSION
          value: "2.3.0"
        - name: GRAY_ENABLED
          value: "true"
        - name: NACOS_METADATA_VERSION
          value: "2.3.0"
        - name: NACOS_METADATA_GRAY
          value: "true"
        - name: NACOS_METADATA_GRAY_VERSION
          value: "v2.3.0-canary"
        resources:
          requests:
            cpu: "1"
            memory: "2Gi"
          limits:
            cpu: "2"
            memory: "4Gi"
```

#### 7.5 Istio DestinationRule

```yaml
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: order-core-destination
spec:
  host: order-core-svc
  subsets:
  - name: stable
    labels:
      version: "2.2.0"
  - name: canary
    labels:
      version: "2.3.0"
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 100
---
# Gateway 鍏ュ彛璺敱
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: gateway-ingress
spec:
  hosts:
  - "api.omplatform.com"
  gateways:
  - omplatform-gateway
  http:
  - match:
    - headers:
        x-gray-tag:
          prefix: "v2.3.0"
    route:
    - destination:
        host: gateway-svc
        port:
          number: 8080
      weight: 100
  - route:
    - destination:
        host: gateway-svc
        port:
          number: 8080
      weight: 100
```

### 8. 鏁版嵁闅旂鏈哄埗

```
鐏板害鏁版嵁闅旂鏂规
鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

鍘熷垯锛?  1. 鐏板害娴侀噺鍐欑伆搴︽暟鎹紝绋冲畾娴侀噺鍐欑ǔ瀹氭暟鎹?  2. 鐏板害鏁版嵁涓嶅奖鍝嶇ǔ瀹氭暟鎹殑姝ｇ‘鎬у拰瀹屾暣鎬?  3. 鐏板害鐗堟湰涓嬬嚎鏃讹紝鐏板害鏁版嵁鍙竻鐞?
鈹屸攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?鏁版嵁灞?         鈹?闅旂鏂规                                    鈹?鈹溾攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?OceanBase      鈹?鏂规 A锛堟帹鑽愶級锛氬悓琛?+ gray_tag 鍒?             鈹?鈹?               鈹?  order 琛ㄥ鍔?gray_tag VARCHAR(32)          鈹?鈹?               鈹?  鐏板害璇锋眰 鈫?gray_tag = "v2.3.0-canary"       鈹?鈹?               鈹?  绋冲畾璇锋眰 鈫?gray_tag = ""                    鈹?鈹?               鈹?  浼樼偣锛氶檷绾ф椂鏃犻渶鍒囨崲琛ㄥ悕锛屾煡璇㈠姞 WHERE 鏉′欢     鈹?鈹?               鈹?                                             鈹?鈹?               鈹?鏂规 B锛堝閫夛級锛氳〃鍚庣紑 _canary                   鈹?鈹?               鈹?  order 鈫?order_canary                        鈹?鈹?               鈹?  浼樼偣锛氱墿鐞嗛殧绂伙紝鏁版嵁娓呯悊绠€鍗?                   鈹?鈹?               鈹?  缂虹偣锛氶檷绾у垏鎹㈠鏉?                           鈹?鈹溾攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?Redis          鈹?key 鍓嶇紑闅旂                                  鈹?鈹?               鈹?  绋冲畾锛歰rder:123 鈫?{...}                      鈹?鈹?               鈹?  鐏板害锛歝anary:order:123 鈫?{...}               鈹?鈹?               鈹?  鐏板害 Key 璁剧疆 TTL = 鐏板害鍛ㄦ湡 + 7d             鈹?鈹溾攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?RocketMQ       鈹?Topic 鍓嶇紑闅旂                                鈹?鈹?               鈹?  绋冲畾锛歰rder_event 鈫?绋冲畾 Consumer 娑堣垂         鈹?鈹?               鈹?  鐏板害锛歝anary_order_event 鈫?鐏板害 Consumer 娑堣垂  鈹?鈹?               鈹?  鐏板害娑堟伅 Header 甯?grayTag                    鈹?鈹溾攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹尖攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?鈹?Elasticsearch  鈹?绱㈠紩鍚庣紑闅旂                                   鈹?鈹?               鈹?  绋冲畾锛歰rders-2026-06                         鈹?鈹?               鈹?  鐏板害锛歰rders-2026-06-canary                  鈹?鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹粹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?```

#### MQ 鐏板害璺敱鍣?
```java
/**
 * MQ 鐏板害璺敱鍣?鈥?鐏板害娑堟伅鍙戦€佸埌鐏板害 Topic
 * 鐏板害 Producer 浣跨敤鐙珛 Topic锛岀伆搴?Consumer 璁㈤槄鐏板害 Topic
 */
@Component
public class MqGrayRouter {

    private static final String CANARY_TOPIC_PREFIX = "canary_";

    /** 鏍规嵁 GrayContext 鍐冲畾瀹為檯鍙戦€佺殑 Topic */
    public String resolveTopic(String originalTopic) {
        if (GrayContext.isGray()) {
            return CANARY_TOPIC_PREFIX + originalTopic;
        }
        return originalTopic;
    }

    /** 鍦ㄦ秷鎭ご涓坊鍔犵伆搴︽爣璁?*/
    public Message<?> enrichMessage(Message<?> message) {
        if (!GrayContext.isGray()) {
            return message;
        }
        return MessageBuilder.fromMessage(message)
                .setHeader("grayTag", GrayContext.getGrayVersion())
                .build();
    }
}
```

### 9. 鏁呴殰淇濇姢鏈哄埗锛? 灞傦級

```
鏁呴殰淇濇姢鏈哄埗锛堟寜瑙﹀彂浼樺厛绾э級
鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

1锔忊儯 涓诲姩鐔旀柇
   鏉′欢锛氱伆搴﹂敊璇巼 > 1%锛?min 绐楀彛锛夋垨 P99 > 绋冲畾鐗?2x
   鍔ㄤ綔锛欰pollo grayPercentage=0锛堣嚜鍔?鎵嬪姩锛?   鎭㈠锛氭墍鏈夌伆搴︽祦閲忓嵆鏃跺洖閫€鍒扮ǔ瀹氱増
   鑰楁椂锛? 5s锛圓pollo 鐑洿鏂帮級

2锔忊儯 闄嶇骇淇濇姢
   鏉′欢锛氱伆搴﹀疄渚嬫棤鍝嶅簲鎴栧叏閮ㄤ笉鍙敤
   鍔ㄤ綔锛欸rayTagRouter 鑷姩闄嶇骇 鈫?璺敱鍒扮ǔ瀹氱増瀹炰緥
   褰卞搷锛氱伆搴︾敤鎴蜂粛鍙敤锛屼絾浣撻獙鐨勬槸绋冲畾鐗堝姛鑳?   鏃ュ織锛欸rayFallbackCount + warning 鏃ュ織

3锔忊儯 鏁版嵁鍥炴粴
   鏉′欢锛氱伆搴︾増鏈啓鍏ヤ簡閿欒鏁版嵁
   鍔ㄤ綔锛氬惎鐢ㄧǔ瀹氱増鏁版嵁鎭㈠锛堝熀浜?gray_tag 鍒楄繃婊わ級
   鏂规锛氬悓琛?+ gray_tag 鈫?WHERE gray_tag='' 杩囨护鐏板害鏁版嵁
         鐙珛琛?鈫?鐏板害鏁版嵁鏁翠綋娓呯悊

4锔忊儯 鐗堟湰鍥為€€
   鏉′欢锛氱伆搴︾増鏈瓨鍦ㄦ牴鏈€х己闄?   鍔ㄤ綔锛?   a) Apollo 閰嶇疆鍒犻櫎鐏板害瑙勫垯
   b) K8s 缂╁鐏板害 Pod
   c) 淇濈暀鐏板害鏁版嵁鍋?root cause 鍒嗘瀽

5锔忊儯 瀹夊叏鎶ゆ爮
   a) 鐏板害姣斾緥涓婇檺淇濇姢锛歮axGrayPercentage锛堥粯璁?50%锛?   b) 鐏板害 Pod 鏁颁笅闄愪繚鎶わ細minGrayReplicas锛堥粯璁?2锛?   c) 鐏板害璇锋眰瓒呮椂淇濇姢锛歡rayRequestTimeout锛堥粯璁?30s锛?   d) 鐏板害闅旂鏁版嵁 TTL锛氳嚜鍔ㄦ竻鐞?7 澶╁墠鐨勭伆搴︽暟鎹?```

### 10. 鎸囨爣涓庡憡璀?
#### 10.1 Prometheus 鎸囨爣

```java
// === 鐏板害璇锋眰鎸囨爣 ===

// 鐏板害璇锋眰鎬婚噺锛堟寜鐏板害鐗堟湰銆佺瓥鐣ャ€佹湇鍔″悕锛?Counter.builder("gray.request.total")
    .tag("grayVersion", version)
    .tag("strategy", strategy)       // user-whitelist/percentage/header
    .tag("service", serviceName)
    .register(meterRegistry);

// 鐏板害璇锋眰閿欒鏁?Counter.builder("gray.request.error")
    .tag("grayVersion", version)
    .tag("service", serviceName)
    .tag("errorType", type)          // biz_exception/timeout/circuit_break
    .register(meterRegistry);

// 鐏板害璇锋眰鑰楁椂
Timer.builder("gray.request.duration")
    .tag("grayVersion", version)
    .tag("service", serviceName)
    .tag("api", apiName)
    .publishPercentiles(0.5, 0.9, 0.99)
    .register(meterRegistry);

// 鐏板害璺敱闄嶇骇娆℃暟锛堢伆搴﹀疄渚嬩笉鍙敤 鈫?闄嶇骇鍒扮ǔ瀹氱増锛?Counter.builder("gray.route.degradation")
    .tag("grayVersion", version)
    .tag("targetService", serviceName)
    .register(meterRegistry);

// 鐏板害璺敱 Fallback 娆℃暟锛圙rayTag 鏃犲搴旂増鏈槧灏勶級
Counter.builder("gray.route.fallback")
    .tag("grayVersion", version)
    .register(meterRegistry);

// 鐏板害鐗堟湰涓€鑷存€э紙鐏板害璇锋眰鍒拌揪鐨勬湇鍔℃槸鍚﹀尮閰嶉鏈熺増鏈級
Gauge.builder("gray.version.consistency", this,
        VersionConsistencyGauge::getConsistencyPercent)
    .tag("sourceService", source)
    .tag("targetService", target)
    .register(meterRegistry);

// 娲昏穬鐏板害瀹炰緥鏁?Gauge.builder("gray.instance.active")
    .tag("service", serviceName)
    .tag("version", version)
    .register(meterRegistry);
```

#### 10.2 鍛婅瑙勫垯

```yaml
groups:
  - name: gray_release_alerts
    interval: 30s
    rules:

      # P1: 鐏板害閿欒鐜囬鍗?鈫?鑷姩鐔旀柇瑙﹀彂鏉′欢
      - alert: GrayErrorRateSpike
        expr: |
          rate(gray_request_error_total[5m])
          / rate(gray_request_total[5m]) * 100 > 1.0
        for: 3m
        labels:
          severity: P1
          team: sre
        annotations:
          summary: "鐏板害鐗堟湰閿欒鐜囪秴杩?1%"
          description: "鐗堟湰 {{ $labels.grayVersion }} 閿欒鐜?{{ $value }}%"

      # P1: 鐏板害鐗堟湰涓€鑷存€т涪澶?      - alert: GrayVersionInconsistency
        expr: gray_version_consistency < 99
        for: 1m
        labels:
          severity: P1
          team: sre
        annotations:
          summary: "鐏板害鐗堟湰涓€鑷存€т綆浜?99%"
          description: "鏈嶅姟 {{ $labels.targetService }} 涓€鑷存€т粎 {{ $value }}%"

      # P2: 鐏板害闄嶇骇杩囧锛堢伆搴﹀疄渚嬩笉澶燂級
      - alert: GrayHighDegradation
        expr: rate(gray_route_degradation_total[10m]) > 10
        for: 5m
        labels:
          severity: P2
          team: sre
        annotations:
          summary: "鐏板害瀹炰緥涓嶈冻锛岄檷绾ц繃澶?
          description: "鐗堟湰 {{ $labels.grayVersion }} 10min 闄嶇骇 {{ $value }} 娆?

      # P2: 鐏板害 P99 寤惰繜瓒呰繃绋冲畾鐗?1.5 鍊?      - alert: GrayLatencyAnomaly
        expr: |
          histogram_quantile(0.99, rate(gray_request_duration_seconds_bucket[5m]))
          > histogram_quantile(0.99,
               rate(request_duration_seconds_bucket{grayVersion=""}[5m])) * 1.5
        for: 5m
        labels:
          severity: P2
          team: sre
        annotations:
          summary: "鐏板害鐗堟湰 P99 瓒呰繃绋冲畾鐗?1.5 鍊?
```

---

## 澶囬€夋柟妗堣瘎浼?
| 缁村害 | 鏂规 A锛欴ubbo Filter锛堥€変腑锛?| 鏂规 B锛欼stio-only | 鏂规 C锛歂acos Metadata + 鑷畾涔?LoadBalancer |
|------|------|------|------|
| **鍏ㄩ摼璺鐩?* | 鉁?鎵€鏈?Dubbo 璋冪敤鑷姩浼犻€?| 鈿狅笍 浠?HTTP锛圖ubbo 鍗忚涓嬫爣绛鹃€忎紶涓嶆垚鐔燂級 | 鉂?闇€瑕侀澶栨満鍒惰法閾捐矾浼犻€?tag |
| **浠ｇ爜渚靛叆** | 涓紙Filter/Router SPI锛屾棤涓氬姟浠ｇ爜渚靛叆锛?| 闆?| 涓紙鏀归€?LoadBalancer + 璋冪敤閾句紶閫掗€昏緫锛?|
| **鐏板害瑙勫垯绮剧粏搴?* | 楂橈紙鐧藉悕鍗?姣斾緥/Header/Path/澶氱瓥鐣ョ粍鍚堬級 | 浣庯紙浠?header 鍖归厤锛?| 涓紙浠?metadata 鍖归厤锛?|
| **Dubbo 鍏煎鎬?* | 鍘熺敓 Dubbo SPI 鏈哄埗 | 闇€ Istio 1.5+ 棰濆閰嶇疆 | 闇€鏀归€?Dubbo LoadBalancer |
| **Apollo 闆嗘垚** | 澶╃劧闆嗘垚锛岄厤缃彉鏇寸绾х敓鏁?| 闇€棰濆鍚屾鏈哄埗 | 闇€棰濆鍚屾鏈哄埗 |
| **杩愮淮澶嶆潅搴?* | 浣庯紙涓€娆?SPI 閰嶇疆锛屽叏鏈嶅姟鐢熸晥锛?| 楂橈紙姣忎釜鏈嶅姟鐙珛 VirtualService锛?| 涓?|

---

## 瀹炴柦璁″垝

| 闃舵 | 浠诲姟 | 宸ユ椂 | 浜у嚭 |
|------|------|------|------|
| **P1 鍩虹璁炬柦** | GrayContext + GrayTag 鏁版嵁缁撴瀯 | 0.5d | ThreadLocal 涓婁笅鏂?+ 鍊煎璞?|
| | GrayTagConsumerFilter + GrayTagProviderFilter锛圖ubbo SPI锛?| 1d | 2 涓?Filter + SPI 閰嶇疆 + 鍗曞厓娴嬭瘯 |
| | GrayTagRouter锛圖ubbo SPI RouterFactory锛?| 1d | Router + SPI 閰嶇疆 + 鍗曞厓娴嬭瘯 |
| | Dubbo SPI 閰嶇疆 + 鑷姩鍖栨祴璇?| 0.5d | 鍏ㄦā鍧?Filter 鍔犺浇楠岃瘉 |
| | **灏忚** | **3d** | |
| **P2 Gateway 灞?* | GatewayGrayFilter锛圫pring Cloud Gateway GlobalFilter锛?| 1d | 鐏板害璇嗗埆 + RpcContext 鍐欏叆 |
| | 鐏板害鍒ゅ畾閫昏緫锛坔eader/user-whitelist/percentage锛?| 0.5d | 澶氱鐏板害绛栫暐瀹炵幇 |
| | 绔埌绔伆搴﹂摼璺祴璇?| 0.5d | Gateway 鈫?order-core 鈫?payment 浼犻€掗獙璇?|
| | **灏忚** | **2d** | |
| **P3 Apollo 瑙勫垯** | GrayRoutingRule 鏁版嵁妯″瀷 | 0.5d | POJO + JSON 搴忓垪鍖?|
| | GrayRoutingRuleService锛圓pollo 鐩戝惉 + 鐑洿鏂帮級 | 0.5d | 瀹炴椂閰嶇疆鍚屾 |
| | 鐏板害瑙勫垯绠＄悊 API锛堝彲閫夛紝杩愮淮鐢級 | 0.5d | 鍚庡彴绠＄悊鎺ュ彛 + 閰嶇疆鏍￠獙 |
| | **灏忚** | **1.5d** | |
| **P4 鍏煎鎬?闅旂** | VersionCompatibilityChecker | 1d | 璇箟鐗堟湰瑙ｆ瀽 + 鍏煎鎬ф牎楠?|
| | Nacos metadata 鐗堟湰娉ㄥ唽锛堝悇鏈嶅姟 bootstrap.yml锛?| 0.5d | 鏈嶅姟鐗堟湰鍏冩暟鎹爣鍑嗗寲 |
| | 鏁版嵁闅旂绛栫暐锛圖B/Redis/MQ/ES锛?| 1d | 鐏板害鏁版嵁闅旂瀹炵幇 + MqGrayRouter |
| | **灏忚** | **2.5d** | |
| **P5 鐩戞帶鍛婅** | Prometheus 鐏板害鎸囨爣娉ㄥ唽锛? 涓寚鏍囷級 | 0.5d | Metrics 鍩嬬偣 |
| | Grafana 鐏板害鐪嬫澘 | 0.5d | 鐏板害澶х洏锛堣姹傞噺/閿欒鐜?寤惰繜/涓€鑷存€э級 |
| | 鍛婅瑙勫垯閰嶇疆锛圥1脳2 + P2脳2锛?| 0.5d | AlertManager 瑙勫垯 |
| | **灏忚** | **1.5d** | |
| **P6 闆嗘垚楠岃瘉** | 鍐呴儴鐜鍏ㄩ摼璺伆搴︽紨缁?| 1d | 鐏板害娴佺▼ SOP 楠岃瘉 |
| | Istio DestinationRule + VirtualService 閮ㄧ讲閰嶇疆 | 0.5d | 鐗堟湰 subset + 鍏ュ彛璺敱 |
| | 鍏ㄩ摼璺伆搴﹀帇娴嬶紙鐏板害/绋冲畾娣疯窇绋冲畾鎬э級 | 1d | 鍘嬫祴鎶ュ憡 |
| | 鐏板害鍙戝竷鏂囨。 + 杩愮淮鎵嬪唽 | 0.5d | 鎿嶄綔鎵嬪唽 + 鍥炴粴棰勬 |
| | **灏忚** | **2d** | |
| **鎬昏** | | **12.5d** | |

---

## 涓婄嚎妫€鏌ユ竻鍗?
### 鍩虹璁炬柦
- [ ] `GrayContext` + `GrayTag` 宸插彂甯冨埌 common 妯″潡锛堢嚎绋嬪畨鍏ㄥ凡楠岃瘉锛?- [ ] Dubbo SPI 閰嶇疆鏂囦欢宸插垱寤猴細`META-INF/dubbo/org.apache.dubbo.rpc.Filter`
- [ ] Dubbo SPI 閰嶇疆鏂囦欢宸插垱寤猴細`META-INF/dubbo/org.apache.dubbo.rpc.cluster.RouterFactory`
- [ ] `GrayTagConsumerFilter` 鍏ㄥ眬鍔犺浇宸查獙璇侊紙consumer 绔級
- [ ] `GrayTagProviderFilter` 鍏ㄥ眬鍔犺浇宸查獙璇侊紙provider 绔級
- [ ] `GrayTagRouter` 鍏ㄥ眬鍔犺浇宸查獙璇侊紙router SPI锛?
### Gateway
- [ ] `GatewayGrayFilter` 宸查儴缃插埌 gateway 鏈嶅姟
- [ ] 鐏板害鍒ゅ畾閫昏緫瀹屾暣锛坔eader/鐧藉悕鍗?姣斾緥/Header 瑙勫垯锛?- [ ] `RpcContext.setAttachment("grayTag", ...)` 宸茶缃?- [ ] 鍝嶅簲澶?`x-gray-result` 宸叉坊鍔狅紙Debug 鐢級
- [ ] `GrayContext.clear()` 鍦?`doFinally` 涓墽琛?
### Nacos 娉ㄥ唽
- [ ] 姣忎釜鏈嶅姟鐨?`bootstrap.yml` 宸查厤缃?`version` metadata
- [ ] 鐏板害閮ㄧ讲鏃?`gray=true` + `grayVersion` metadata 宸查厤缃?- [ ] `${APP_VERSION}` / `${GRAY_ENABLED}` 鐜鍙橀噺宸叉敞鍏?
### Apollo 閰嶇疆
- [ ] `gray-routing-rule` namespace 宸插垱寤?- [ ] 鐏板害瑙勫垯 JSON 閰嶇疆宸茬敓鏁堬紙鍚?`tagToVersionMapping`锛?- [ ] Apollo 閰嶇疆鍙樻洿鐩戝惉宸插疄鐜帮紙`GrayRoutingRuleService`锛?- [ ] 鍥炴粴棰勬锛欰pollo `grayPercentage=0` 鍙嵆鏃舵挙鍥炵伆搴?
### 鍏煎鎬?- [ ] `VersionCompatibilityChecker` 宸插疄鐜?- [ ] 鏈嶅姟 Nacos metadata 涓?`requires` 瀛楁宸查厤缃?- [ ] 鐏板害璇锋眰闄嶇骇鍒扮ǔ瀹氱増鐨勯摼璺獙璇侀€氳繃

### 鏁版嵁闅旂
- [ ] 鐏板害 DB 绛栫暐宸茬‘璁わ紙鍚岃〃 + `gray_tag` 鍒楋級
- [ ] 鐏板害 Redis key 鍓嶇紑闅旂宸插疄鐜?- [ ] 鐏板害 MQ Topic 闅旂宸插疄鐜帮紙`MqGrayRouter`锛?- [ ] 鐏板害 ES 绱㈠紩闅旂宸插疄鐜?- [ ] 鐏板害闅旂鏁版嵁 TTL 娓呯悊绛栫暐宸查厤缃?
### 鐩戞帶
- [ ] 6 涓伆搴?Prometheus 鎸囨爣宸叉敞鍐?- [ ] Grafana 鐏板害鐪嬫澘宸蹭笂绾?- [ ] P1 鍛婅 `GrayErrorRateSpike` / `GrayVersionInconsistency` 宸插惎鐢?- [ ] P2 鍛婅 `GrayHighDegradation` / `GrayLatencyAnomaly` 宸插惎鐢?
### K8s 閮ㄧ讲
- [ ] Istio `DestinationRule` version subset 宸插垱寤?- [ ] 鐏板害 Pod 鐨?`version` + `gray` label 宸查厤缃?- [ ] 鐏板害 Pod HPA 宸查厤缃紙鏈€灏?2 Pods锛?- [ ] 鐏板害 Pod 璧勬簮闄愬埗宸查厤缃紙閬垮厤褰卞搷绋冲畾鐗堬級

### 娴嬭瘯
- [ ] 鍗曞厓娴嬭瘯锛欸rayTagRouter锛堢ǔ瀹?鐏板害/闄嶇骇鍦烘櫙锛?- [ ] 鍗曞厓娴嬭瘯锛欸rayContext ThreadLocal 娓呯悊楠岃瘉
- [ ] 闆嗘垚娴嬭瘯锛欸ateway 鈫?鐏板害 order-core 鈫?鐏板害 payment
- [ ] 闆嗘垚娴嬭瘯锛氱伆搴?order-core 鈫?绋冲畾 payment锛堢伆搴﹂檷绾э級
- [ ] 闆嗘垚娴嬭瘯锛氭棤鐏板害鏍囩璇锋眰 鈫?绋冲畾鐗堝叏閾捐矾
- [ ] 闆嗘垚娴嬭瘯锛欰pollo 閰嶇疆鍙樻洿瀹炴椂鐢熸晥
- [ ] 鏁呴殰娉ㄥ叆锛氱伆搴﹀疄渚嬪叏閮ㄥ畷鏈?鈫?楠岃瘉鑷姩闄嶇骇
- [ ] 鏁呴殰娉ㄥ叆锛氱伆搴﹁姹傛寔缁姤閿?鈫?楠岃瘉鐔旀柇锛圓pollo grayPercentage=0锛?
---

## 涓庣幇鏈夋枃妗ｇ殑鍏宠仈

| 鏂囨。 | 鍏宠仈鍐呭 |
|------|---------|
| `docs/diagrams/canary-release.md` | 鏈?ADR 鏄叏閾捐矾鐏板害鏂规锛屼笌涔嬩簰琛ワ細鍗曟湇鍔＄伆搴︽祦绋嬬殑 Istio 闃舵鍙洿鎺ュ鐢?|
| ADR-017 涓氬姟绾跨墿鐞嗛殧绂?| Apollo 鐏板害閰嶇疆妯″紡澶嶇敤锛涙暟鎹殧绂绘柟妗堬紙琛ㄥ悗缂€銆丒S 鐙珛绱㈠紩锛夋ā寮忓彲鍊熼壌 |
| ADR-020 Saga 鍒嗗竷寮忎簨鍔?| Saga 缂栨帓鍣ㄩ渶鎰熺煡 grayTag锛氱伆搴﹁姹傝皟鐢ㄧ伆搴︾増鏈殑 Saga 姝ラ锛岃ˉ鍋块€昏緫鍚岀悊 |
| ADR-021 寤惰繜浠诲姟璋冨害 | 鐏板害闅旂锛氬欢杩熶换鍔′篃闇€鍖哄垎鐏板害/绋冲畾鐗堢殑娑堟伅閫氶亾锛孧qGrayRouter 缁熶竴澶勭悊 |
| ADR-018 鐩戞帶澶х洏澧炲己 | 鐏板害澶х洏涓庝笟鍔＄洃鎺уぇ鐩樺悎骞讹紝鐏板害鎸囨爣鍙祵鍏ヨ祫閲?涓€鑷存€х湅鏉?|
| 鏋舵瀯鏂囨。 搂5.2 Dubbo RPC | 琛ュ厖 Dubbo Filter 閾捐矾鐨勭伆搴?Filter 鎻忚堪 |
| 鏋舵瀯鏂囨。 搂5.5 Istio | 琛ュ厖 DestinationRule version subset 绛栫暐 |
| 鏋舵瀯鏂囨。 搂7 閮ㄧ讲 | 琛ュ厖鐏板害閮ㄧ讲绛栫暐鍜岀増鏈鐞嗚鑼?|
