# ADR-027锛氬彲瑙傛祴鎬ф灦鏋?
## 鐘舵€?
宸叉帴鍙?
---

## 鑳屾櫙

### 鐜扮姸鍒嗘瀽

璁㈠崟涓彴瀹氫箟浜?20+ 寰湇鍔″拰 6 绉嶄腑闂翠欢锛圤ceanBase / ES / Redis / RocketMQ / Nacos / Apollo锛夛紝浣?*缂哄皯缁熶竴鐨勫彲瑙傛祴鎬ф灦鏋勮璁?*銆傚綋鍓嶇姸鎬侊細

**闂 1锛氭棩蹇楃瓥鐣ユ湭瀹氫箟**  
鎵€鏈?ADR 涓病鏈夊畾涔夋棩蹇楄鑼冣€斺€旀棩蹇楁牸寮忔槸浠€涔堬紙text vs JSON锛夛紵鏄惁鍖呭惈 traceId锛熸棩蹇楃骇鍒鑼冨浣曪紵鏃ュ織濡備綍鑱氬悎鍜屾悳绱紵杩欎簺鍏ㄩ儴绌虹櫧銆傜嚎涓婃帓鏌ラ棶棰樻椂锛岃繍缁撮渶瑕?SSH 鍒?Pod 閫愪釜缈绘棩蹇椼€?
**闂 2锛氭棤缁熶竴鎸囨爣鍛藉悕瑙勮寖**  
ADR-012/013/014 绛夐兘瀹氫箟浜嗗垎鏁ｇ殑 Prometheus 鎸囨爣锛坋s_index_size_bytes銆乧anal_delay_seconds銆乧ache_hit_ratio锛夛紝浣嗙己灏?*缁熶竴鍛藉悕瑙勮寖**銆備笉鍚屽紑鍙戣€呮寜涓嶅悓椋庢牸瀹氫箟鎸囨爣锛屾湭鏉ュ彲鑳藉嚭鐜版寚鏍囨贩涔卞拰闅句互鑱氬悎鐨勯棶棰樸€?
**闂 3锛氶摼璺拷韪瓥鐣ヤ笉瀹屾暣**  
瀹瑰櫒鍥惧垪鍑轰簡 SkyWalking锛屼絾娌℃湁閲囨牱绛栫暐銆傛槸 100% 鍏ㄩ噺閲囨牱杩樻槸姒傜巼閲囨牱锛熺敓浜у拰寮€鍙戠幆澧冩槸鍚︿笉鍚岋紵褰撳嚭鐜版參 trace 鏃讹紝鏄惁鑳借嚜鍔ㄤ繚鐣欎互甯姪鎺掓煡锛?
**闂 4锛氭棩蹇?鎸囨爣/閾捐矾涓夎€呭壊瑁?*  
褰撳墠娌℃湁璁捐 traceId 濡備綍鍦ㄦ棩蹇椼€佹寚鏍囧拰閾捐矾涔嬮棿鍏宠仈銆傞亣鍒颁竴涓嚎涓婃晠闅滄椂锛岃繍缁翠汉鍛橀渶瑕佸湪 3 涓笉鍚岀郴缁熼棿鎵嬪姩鍏宠仈淇℃伅銆?
**闂 5锛氱己灏?SLI/SLO 妗嗘灦**  
"璁㈠崟鍒涘缓鎴愬姛鐜囧簲璇ユ槸澶氬皯锛?銆?鏀粯 P99 寤惰繜涓嶈兘瓒呰繃澶氬皯锛?鈥斺€旇繖浜涚洰鏍囨病鏈夋寮忓畾涔夈€傜己灏?SLO 瀵艰嚧 BAU锛堟棩甯歌繍缁达級娌℃湁鏄庣‘鐨勭ǔ瀹氭€у拰鎬ц兘鐩爣椹卞姩銆?
**闂 6锛氬憡璀︾瓥鐣ュ垎鏁?*  
鍚?ADR 瀹氫箟浜嗛浂鏁ｇ殑鍛婅瑙勫垯锛圓DR-013: Canal 寤惰繜 > 10s P1銆丄DR-024: SQL 鎬ц兘鍩虹嚎閫€鍖?P1锛夛紝浣嗙己灏戠粺涓€鍛婅鍒嗙骇銆侀潤榛樸€佽仛鍚堝拰鍗囩骇绛栫暐銆?
### 鐩爣

1. **缁熶竴鏃ュ織瑙勮寖**锛欽SON 缁撴瀯鍖栥€丮DC 鑷姩娉ㄥ叆銆佹寜鏈嶅姟鑱氬悎
2. **缁熶竴鎸囨爣瑙勮寖**锛氬懡鍚嶈鍒欍€佸叕鍏辨爣绛俱€佹寚鏍囨不鐞?3. **閾捐矾閲囨牱绛栫暐**锛氬垎绾ч噰鏍枫€佹參 trace 鑷姩淇濈暀
4. **涓夋敮鏌卞叧鑱?*锛歵raceId 绌块€忔棩蹇?鎸囨爣-閾捐矾
5. **SLI/SLO 妗嗘灦**锛氭牳蹇冧笟鍔℃祦鐨勬湇鍔＄瓑绾у畾涔?6. **鍛婅绛栫暐**锛氬垎绾с€佽矾鐢便€侀潤榛樸€佸崌绾ц鑼?
### 鏈瀹氫箟

| 鏈 | 璇存槑 |
|------|------|
| **鍙娴嬫€э紙Observability锛?* | 閫氳繃 Logs/Traces/Metrics 涓夋敮鏌辨帹鏂郴缁熷唴閮ㄧ姸鎬佺殑鑳藉姏 |
| **SLI** | 鏈嶅姟绛夌骇鎸囨爣锛圫ervice Level Indicator锛夛紝濡傝姹傚欢杩熴€侀敊璇巼 |
| **SLO** | 鏈嶅姟绛夌骇鐩爣锛圫ervice Level Objective锛夛紝濡?P99 < 100ms |
| **Trace** | 璺ㄦ湇鍔＄殑璇锋眰璋冪敤閾撅紝鐢卞涓?Span 缁勬垚 |
| **Span** | Trace 涓殑鍗曚釜鎿嶄綔鍗曞厓锛堝涓€涓?Dubbo 璋冪敤銆佷竴涓?DB 鏌ヨ锛?|
| **MDC** | Mapped Diagnostic Context锛孡ogback 鐨勪笂涓嬫枃鏄犲皠锛岀敤浜庢惡甯?traceId |
| **Head-based Sampling** | 鍦ㄨ姹傚叆鍙ｅ鍐冲畾鏄惁閲囨牱锛堟鐜囧喅瀹氾級 |
| **Tail-based Sampling** | 璇锋眰瀹屾垚鍚庢牴鎹壒寰侊紙濡傚欢杩?> N锛夊喅瀹氭槸鍚︿繚鐣?trace |

---

## 鍐崇瓥

### 鏃ュ織鑱氬悎

**鏂规锛欽SON 缁撴瀯鍖栨棩蹇?+ Loki**

| 缁村害 | Loki锛堥€変腑锛?| ELK | 鑷缓 |
|------|------------|-----|------|
| **Grafana 鐢熸€?* | 鉁?鍘熺敓闆嗘垚 | 鈻?闇€閰嶇疆鏁版嵁婧?| 鉁?鍘熺敓 |
| **杩愮淮鎴愭湰** | 鉁?杞婚噺锛堟棤 ES 闆嗙兢锛?| 鉂?ES 闆嗙兢缁存姢 | 鉂?楂?|
| **鏌ヨ璇硶** | 鉁?LogQL | 鉁?KQL + Lucene | 鑷畾涔?|
| **鎴愭湰** | 鉁?瀵硅薄瀛樺偍锛屼綆鎴愭湰 | 鈿狅笍 ES 瀛樺偍鎴愭湰楂?| 鉂?|
| **鎵╁睍鎬?* | 鉁?鏃犵姸鎬佽鍐欏垎绂?| 鈿狅笍 ES 鍒嗙墖瑙勫垝 | 鑷畾涔?|

**鐞嗙敱**锛欸rafana 宸叉槸鏃㈠畾鐩戞帶骞冲彴锛孡oki 鍘熺敓闆嗘垚鍒?Grafana 鐢熸€侊紝涓旀敮鎸佸璞″瓨鍌紙OSS/OSSFS锛夛紝杩愮淮鎴愭湰杩滀綆浜?ELK銆?
### 鎸囨爣鏍囧噯

**鏂规锛歁icrometer锛圡icrometer Registry锛? Prometheus + 缁熶竴鍛藉悕瑙勮寖**

### 閾捐矾杩借釜

**鏂规锛歋kyWalking锛堝凡鍐崇瓥锛? Head-based 鍒嗗眰閲囨牱 + Tail-based 鑷姩淇濈暀**

### 鍏宠仈鏂规

**鏂规锛歵raceId 绌块€忎笁鏀煴**鈥斺€旀瘡涓棩蹇楄鎼哄甫 traceId锛岄€氳繃 traceId 鍏宠仈 Trace 鍜?Metrics

---

## 璇︾粏璁捐

### 1. 缁撴瀯鍖栨棩蹇楄鑼?
#### 鏃ュ織鏍煎紡

```json
{
  "@timestamp": "2026-06-12T10:30:00.123+08:00",
  "level": "INFO",
  "service": "order-core",
  "traceId": "skywalking_trace_id_or_span_id",
  "spanId": "span_id",
  "userId": "u_10086",
  "orderId": "ORD20260612100001",
  "method": "createOrder",
  "duration_ms": 45,
  "message": "璁㈠崟鍒涘缓鎴愬姛锛岃鍗曞彿锛歄RD20260612100001",
  "error": null,
  "extra": {
    "skuId": "SKU001",
    "quantity": 2
  }
}
```

#### Logback 閰嶇疆妯″紡

```xml
<!-- logback-spring.xml -->
<configuration>
    <!-- JSON 鏍煎紡锛堢敓浜х幆澧冿級 -->
    <springProfile name="prod,staging">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <!-- 鑷畾涔?MDC 瀛楁 -->
                <includeMdcKeyName>traceId</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
                <includeMdcKeyName>orderId</includeMdcKeyName>
                <includeMdcKeyName>spanId</includeMdcKeyName>
            </encoder>
        </appender>
    </springProfile>

    <!-- 鏂囨湰鏍煎紡锛堝紑鍙戠幆澧冿級 -->
    <springProfile name="dev,default">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{traceId}] - %msg%n</pattern>
            </encoder>
        </appender>
    </springProfile>
</configuration>
```

#### MDC 鑷姩娉ㄥ叆

閫氳繃 Dubbo Filter + Web Filter 鑷姩娉ㄥ叆 MDC 瀛楁锛屼笟鍔′唬鐮佹棤闇€鍏冲績锛?
```java
// Web 灞傦細璇锋眰鍏ュ彛娉ㄥ叆
@Component
public class MdcWebFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        try {
            // traceId 浠?SkyWalking 鐨?Context 鑾峰彇锛屾垨鑷姩鐢熸垚
            String traceId = SkyWalkingContext.current().getTraceId();
            MDC.put("traceId", traceId);
            
            // 鐢ㄦ埛淇℃伅浠?AuthContext 鑾峰彇锛圓DR-026锛?            AuthContext auth = AuthContext.get();
            if (auth != null) {
                MDC.put("userId", auth.getUserId());
            }
            
            chain.doFilter(request, response);
        } finally {
            MDC.clear();  // 蹇呴』娓呯悊锛岄槻姝?ThreadLocal 娉勬紡
        }
    }
}

// Dubbo Provider 绔細閫氳繃 Filter 娉ㄥ叆
@Activate(group = "provider", order = -7000)
public class MdcProviderFilter implements Filter {
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        try {
            String traceId = RpcContext.getServerAttachment().getAttachment("traceId");
            if (traceId != null) {
                MDC.put("traceId", traceId);
            }
            return invoker.invoke(invocation);
        } finally {
            MDC.clear();
        }
    }
}
```

#### 鏃ュ織绾у埆瑙勮寖

| 绾у埆 | 浣跨敤鍦烘櫙 | 绀轰緥 |
|------|---------|------|
| **ERROR** | 闇€瑕佷汉宸ヤ粙鍏ョ殑寮傚父锛屽璋冪敤涓夋柟鏀粯澶辫触銆丏B 杩炴帴鏂紑 | `鏀粯鍥炶皟楠岀澶辫触锛宼ransactionId=xxx` |
| **WARN** | 涓嶉渶瑕佺珛鍗冲鐞嗕絾闇€瑕佹敞鎰忕殑寮傚父锛屽閲嶈瘯銆侀檷绾с€侀檺娴?| `搴撳瓨棰勫崰閲嶈瘯绗?2 娆★紝orderId=xxx` |
| **INFO** | 鍏抽敭涓氬姟浜嬩欢锛屽璁㈠崟鍒涘缓銆佹敮浠樻垚鍔熴€侀€€娆惧畬鎴?| `璁㈠崟鍒涘缓鎴愬姛锛宱rderId=xxx` |
| **DEBUG** | 寮€鍙戞帓鏌ョ敤锛岀敓浜х幆澧冮粯璁ゅ叧闂?| SQL 鍙傛暟銆佷腑闂村彉閲忓€?|

**瑙勫垯**锛?- INFO 琛屾暟锛氭牳蹇冧笟鍔℃祦绋?<= 5 琛?INFO/璇锋眰锛堝お澶氬垯闅句互闃呰锛?- 绂佹 `log.error(e.getMessage())`鈥斺€斿繀椤讳紶鍏ュ紓甯稿璞′互淇濈暀鍫嗘爤
- 绂佹鍦ㄥ惊鐜腑鎵撳嵃 DEBUG 鏃ュ織鈥斺€斿厛鍒ゆ柇 `log.isDebugEnabled()`

### 2. 鎸囨爣鍛藉悕瑙勮寖

#### 鍛藉悕妯″紡

```
<prefix>_<service>_<layer>_<name>[_unit]

prefix  = omplatform        锛堟墍鏈?OMP 鎸囨爣鍥哄畾鍓嶇紑锛?service = order_core        锛堟湇鍔″悕锛屼笅鍒掔嚎鍒嗛殧锛?layer   = api | dubbo | db  锛堝眰绾ф爣璇嗭級
name    = create_order      锛堟寚鏍囧悕锛屼笅鍒掔嚎鍒嗛殧锛?unit    = total | ms | bytes锛堝崟浣嶏紝鍙€夛級
```

#### 绀轰緥

```yaml
# API 灞傛寚鏍?omplatform_order_core_api_create_order_total
omplatform_order_core_api_create_order_duration_ms{quantile="0.99"}
omplatform_order_core_api_create_order_duration_ms{quantile="0.50"}
omplatform_order_core_api_get_order_total

# Dubbo 灞傛寚鏍?omplatform_payment_dubbo_confirm_pay_total{result="success|fail"}
omplatform_inventory_dubbo_deduct_stock_duration_ms

# DB 灞傛寚鏍?omplatform_order_core_db_select_total
omplatform_order_core_db_insert_duration_ms

# 涓棿浠舵寚鏍?omplatform_redis_cache_hit_ratio{instance="order-cache"}
omplatform_rocketmq_order_paid_event_lag_seconds

# 涓氬姟鎸囨爣
omplatform_business_order_created_total{biz_scope="ecommerce|locallife|b2b"}
omplatform_business_payment_success_rate
omplatform_business_refund_amount_total
```

#### 鍏叡鏍囩锛堟瘡涓寚鏍囧繀椤绘惡甯︼級

| 鏍囩 | 璇存槑 | 绀轰緥 |
|------|------|------|
| `service` | 鏈嶅姟鍚?| `order-core` |
| `instance` | 瀹炰緥 ID | `order-core-6f8d9c7b4c-abc12` |
| `version` | 鏈嶅姟鐗堟湰 | `2.3.0` |
| `biz_scope` | 涓氬姟绾?| `ecommerce` |
| `status` | 缁撴灉鐘舵€?| `success/fail` |

#### 鎸囨爣娉ㄥ唽瑙勮寖

```java
@Configuration
public class MetricsConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return registry -> registry.config().commonTags(
            "service", System.getenv("SERVICE_NAME"),
            "version", System.getenv("SERVICE_VERSION")
        );
    }
}

// 浣跨敤鏂瑰紡锛欳ounter
Counter.builder("omplatform.order_core.api.create_order.total")
    .tag("biz_scope", "ecommerce")
    .register(meterRegistry)
    .increment();

// 浣跨敤鏂瑰紡锛歍imer
Timer.builder("omplatform.order_core.api.create_order.duration")
    .tag("biz_scope", "ecommerce")
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(meterRegistry)
    .record(() -> orderService.createOrder(request));

// 浣跨敤鏂瑰紡锛欸auge
Gauge.builder("omplatform.redis.cache.hit_ratio", cacheStats, CacheStats::getHitRate)
    .tag("instance", "order-cache")
    .register(meterRegistry);
```

### 3. 閾捐矾杩借釜绛栫暐

#### 閲囨牱鐜囩瓥鐣?
| 鐜 | 閲囨牱绛栫暐 | 閲囨牱鐜?| 璇存槑 |
|------|---------|--------|------|
| **dev** | 澶撮儴閲囨牱 | 100% | 寮€鍙戣皟璇曢渶瑕佸叏閲忛摼璺?|
| **staging** | 澶撮儴閲囨牱 | 50% | 鍘嬫祴鏈熷彲璋冭嚦 100% |
| **prod** | 澶撮儴閲囨牱 | 5%锛堥粯璁わ級 | 浣庨噰鏍风巼鎺у埗鎴愭湰 |
| **prod锛堟參 trace锛?* | 灏鹃儴閲囨牱 | 鑷姩 | 寤惰繜 > 1s 鐨?trace 鑷姩淇濈暀 |

#### SkyWalking Agent 閰嶇疆

```yaml
# agent/config/agent.config
agent.service_name: ${SW_AGENT_NAME:order-core}
agent.instance_name: ${SW_AGENT_INSTANCE:}
agent.sample_n_per_3_secs: ${SW_SAMPLE_N_PER_3_SECS:-1}  # -1 琛ㄧず鎸夋瘮渚?agent.span_limit_per_segment: ${SW_SPAN_LIMIT:300}

# 閲囨牱閰嶇疆锛圫kyWalking 8.9+锛?agent.sample_rate: ${SW_SAMPLE_RATE:5}   # 5% 澶撮儴閲囨牱
```

#### 鎱?trace 鑷姩淇濈暀

閫氳繃 SkyWalking 鐨?Sampling Hook 鏈哄埗锛屽寤惰繜瓒呰繃闃堝€肩殑璇锋眰鑷姩鏍囪涓轰繚鐣欙細

```java
public class SlowTraceSamplingHook {
    private static final long SLOW_THRESHOLD_MS = 1000;
    
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object traceSlowRequests(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            long duration = System.currentTimeMillis() - start;
            if (duration > SLOW_THRESHOLD_MS) {
                // 寮哄埗淇濈暀璇?trace锛堣皟鐢?SkyWalking API 鎴栬嚜瀹氫箟 Span tag锛?                ActiveSpan.tag("slow_trace", "true");
                ActiveSpan.tag("duration_ms", String.valueOf(duration));
            }
        }
    }
}
```

### 4. 涓夋敮鏌卞叧鑱旀柟妗?
#### traceId 鍏宠仈妯″瀷

```
璇锋眰鍒拌揪 Gateway
  鈹?  鈹溾攢鈹€ SkyWalking 鐢熸垚 TraceId (tid)
  鈹?    鈫?  鈹溾攢鈹€ MDC 娉ㄥ叆 tid 鈫?鎵€鏈夋棩蹇楄鎼哄甫 tid
  鈹?    鈫?  鈹溾攢鈹€ Prometheus 鎸囨爣閫氳繃鏍囩鎼哄甫 tid锛堥噰鏍烽儴鍒嗘寚鏍囷級
  鈹?    鈫?  鈹斺攢鈹€ 涓夊眰鍏宠仈鏂瑰紡锛?
      鏂瑰紡 1锛欸rafana Explore 鈥?杈撳叆 traceId锛屽悓鏃舵悳绱?Loki 鏃ュ織 + SkyWalking trace
      鏂瑰紡 2锛歀oki 鏃ュ織琛?鈥?鐐瑰嚮 traceId 瀛楁锛岃烦杞叧鑱?trace
      鏂瑰紡 3锛歅rometheus 鍛婅 鈥?鍛婅淇℃伅鎼哄甫 traceId 绀轰緥锛屾柟渚胯烦杞畾浣?```

#### 璺ㄦ暟鎹簮璺宠浆閰嶇疆锛圙rafana锛?
```yaml
# Grafana data source configuration
datasources:
  - name: SkyWalking
    type: grafana-skywalking-datasource
    jsonData:
      tracesToLogs:
        datasourceUid: loki
        tags: ['traceId']
        mappedTags:
          - key: service
            value: service
  - name: Loki
    type: loki
    jsonData:
      derivedFields:
        - name: traceId
          type: string
          url:
            datasourceUid: skywalking
            query: '${__value.raw}'
```

### 5. SLI/SLO 妗嗘灦

#### SLI 瀹氫箟

姣忎釜鏍稿績鏈嶅姟瀹氫箟 **5-8 涓?SLI**锛岃鐩栧彲鐢ㄦ€с€佸欢杩熴€佸悶鍚愬拰閿欒鐜囧洓澶х淮搴︼細

```yaml
# order-core SLI 瀹氫箟
order-core:
  availability:
    type: "success_rate"
    query: "sum(rate(omplatform_order_core_api_*_total{status!~'5xx'}[5m])) / sum(rate(omplatform_order_core_api_*_total[5m]))"
    target: 0.99995  # 鍗囩骇鑷?99.995%锛圓DR-040 鍒嗚В锛?
  latency_p99:
    type: "latency"
    query: "histogram_quantile(0.99, sum(rate(omplatform_order_core_api_*_duration_bucket[5m])) by (le))"
    target: 100ms

  latency_p50:
    type: "latency"
    query: "histogram_quantile(0.50, sum(rate(omplatform_order_core_api_*_duration_bucket[5m])) by (le))"
    target: 20ms

  throughput:
    type: "throughput"
    query: "sum(rate(omplatform_order_core_api_create_order_total[5m]))"
    target: 500  # 鏈€浣庡悶鍚愰噺

  error_rate:
    type: "error_rate"
    query: "sum(rate(omplatform_order_core_api_*_total{status=~'5xx'}[5m])) / sum(rate(omplatform_order_core_api_*_total[5m]))"
    target: 0.001  # 0.1%

# payment-core SLI
payment-core:
  payment_success_rate:
    type: "success_rate"
    query: "sum(rate(omplatform_payment_dubbo_confirm_pay_total{result='success'}[5m])) / sum(rate(omplatform_payment_dubbo_confirm_pay_total[5m]))"
    target: 0.9999
  
  payment_latency_p99:
    type: "latency"
    query: "histogram_quantile(0.99, sum(rate(omplatform_payment_dubbo_confirm_pay_duration_bucket[5m])) by (le))"
    target: 200ms
```

#### SLO 鐕冪儳鐜囧憡璀?
```yaml
# 鍩轰簬 SLO 鐕冪儳鐜囩殑鍛婅瑙勫垯
groups:
  - name: slo-burn-rate
    rules:
      # 蹇€熺噧鐑э紙30 澶╁唴娑堣€?5% 棰勭畻 鈫?< 2h 杈惧埌 SLO 杈圭晫锛?      - alert: SLOHighBurnRate
        expr: |
          (
            1 - (sum(rate(omplatform_order_core_api_*_total{status=~"5xx"}[1h]))
                 / sum(rate(omplatform_order_core_api_*_total[1h])))
          ) < 0.9995
          and on()
          (
            1 - (sum(rate(omplatform_order_core_api_*_total{status=~"5xx"}[30d]))
                 / sum(rate(omplatform_order_core_api_*_total[30d])))
          ) < 0.99995  # 鏇存柊鑷?99.995%锛圓DR-040锛?        labels:
          severity: page
        annotations:
          summary: "Order-core SLO 蹇€熺噧鐑?

      # 鎱㈤€熺噧鐑э紙30 澶╁唴娑堣€?10% SLO 棰勭畻 鈫?< 1d 杈惧埌 SLO 杈圭晫锛?      - alert: SLOSlowBurnRate
        expr: |
          (
            1 - (sum(rate(omplatform_order_core_api_*_total{status=~"5xx"}[6h]))
                 / sum(rate(omplatform_order_core_api_*_total[6h])))
          ) < 0.99975  # 鏇存柊鑷?99.975%锛圓DR-040锛?        labels:
          severity: ticket
```

### 6. 鍛婅鍒嗙骇涓庤矾鐢?
#### 鍛婅绛夌骇瀹氫箟

| 绛夌骇 | 鍝嶅簲鏃堕棿 | 閫氱煡鏂瑰紡 | 瑙﹀彂鏉′欢绀轰緥 |
|------|---------|---------|-------------|
| **P0** | 5min | 鐢佃瘽 + 缇?@鎵€鏈変汉 | 鏈嶅姟瀹曟満銆佹敮浠樻垚鍔熺巼 < 99%銆佹暟鎹笉涓€鑷?|
| **P1** | 15min | 缇?@oncall | P99 瓒?3脳 鍩虹嚎銆侀敊璇巼 > 5%銆佸叧閿槦鍒楀爢绉?|
| **P2** | 1h | 缇ゆ姤鍛?| CPU > 85%銆侀潪鍏抽敭鎺ュ彛鍚炲悙涓嬮檷 |
| **P3** | 鏃ユ姤 | 鏃ユ姤鎺ㄩ€?| 纾佺洏浣跨敤 > 80%銆佽瘉涔?7 澶╁唴鍒版湡 |

#### AlertManager 璺敱閰嶇疆

```yaml
route:
  receiver: 'default'
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  routes:
    - match:
        severity: page        # P0/P1
      receiver: 'oncall-phone'
      repeat_interval: 10m
    - match:
        severity: ticket      # P2
      receiver: 'group-chat'
      repeat_interval: 30m
    - match:
        severity: daily       # P3
      receiver: 'daily-report'
      repeat_interval: 24h

receivers:
  - name: 'oncall-phone'
    webhook_configs:
      - url: 'http://alert-bridge/pagerduty'
  - name: 'group-chat'
    webhook_configs:
      - url: 'http://alert-bridge/wechat-work'
  - name: 'daily-report'
    webhook_configs:
      - url: 'http://alert-bridge/daily-digest'
```

### 7. Grafana Dashboard 鍒嗗眰

```yaml
鏂囦欢澶圭粨鏋勶細
  OMP-Business/          # 涓氬姟澶х洏锛堜笟鍔?owner 瑙嗚锛?    - 璁㈠崟瀹炴椂澶х洏锛圙MV / 璁㈠崟閲?/ 鏀粯鎴愬姛鐜?/ 閫€娆剧巼锛?    - 璧勯噾閾捐矾鐪嬫澘锛堟敮浠?閫€娆鹃噾棰濊秼鍔裤€佸璐﹀紓甯革級
    - 鍟嗗缁忚惀鐪嬫澘锛堝悇鍟嗗缁村害璁㈠崟鏁版嵁锛?
  OMP-Service/           # 鏈嶅姟澶х洏锛圫RE 瑙嗚锛?    - order-core 鏈嶅姟鎬昏锛圕PU/鍐呭瓨/GC/QPS/寤惰繜锛?    - payment 鏈嶅姟鎬昏
    - inventory 鏈嶅姟鎬昏
    - 缃戝叧鎬昏锛堣矾鐢辨垚鍔熺巼銆侀檺娴佹鏁帮級

  OMP-Middleware/        # 涓棿浠跺ぇ鐩橈紙DBA 瑙嗚锛?    - OceanBase 鐩戞帶锛圦PS/鎱㈡煡璇?杩炴帴鏁?瀛樺偍锛?    - Elasticsearch 鐩戞帶锛堢储寮?鎼滅储寤惰繜/瀛樺偍锛?    - Redis 鐩戞帶锛堝懡涓巼/鍐呭瓨/鍛戒护寤惰繜锛?    - RocketMQ 鐩戞帶锛堢敓浜?娑堣垂 TPS銆佸爢绉繁搴︺€佹淇￠噺锛?    - Canal 鐩戞帶锛堝悓姝ュ欢杩熴€佹秷璐瑰垎鍖虹姸鎬侊級

  OMP-SLI/               # SLO 鐪嬫澘锛堝钩鍙扮ǔ瀹氭€ц瑙掞級
    - SLO 鐕冪儳鐜囩湅鏉?    - 閿欒棰勭畻鍓╀綑
    - 鏍稿績 SLI 瓒嬪娍
```

### 8. 棰濆鎸囨爣鏉ユ簮锛堜笌鍚?ADR 瀵归綈锛?
| ADR | 鎸囨爣 | 璇存槑 |
|-----|------|------|
| ADR-012 | `omplatform_es_*` | ES 绱㈠紩澶у皬銆佹煡璇㈠欢杩熴€佸瓨鍌ㄨ妭鐪佺巼 |
| ADR-013 | `omplatform_canal_*` | Canal 鍚屾寤惰繜銆佸垎鍖烘秷璐圭姸鎬併€佹晠闅滃垏鎹㈡鏁?|
| ADR-014 | `omplatform_cache_*` | 鐑紦瀛樺懡涓巼銆丮iss 娆℃暟銆佸洖婧愬欢杩?|
| ADR-015 | `omplatform_hpa_*` | HPA 浼哥缉浜嬩欢銆佸綋鍓嶅壇鏈暟銆佺洰鏍囧埄鐢ㄧ巼 |
| ADR-016 | `omplatform_az_*` | AZ 鍒囨崲浜嬩欢銆侀鐑姸鎬併€佺紦瀛樺喎鍚姩鏃堕棿 |
| ADR-018 | `omplatform_fund_*`銆乣omplatform_consistency_*` | 璧勯噾/涓€鑷存€х湅鏉挎寚鏍?|
| ADR-019 | `omplatform_async_job_*` | 寮傛浠诲姟闃熷垪娣卞害銆佹垚鍔?澶辫触鐜?|
| ADR-020 | `omplatform_saga_*` | Saga 鎵ц鐘舵€併€佽ˉ鍋垮け璐ユ鏁?|
| ADR-021 | `omplatform_delayed_task_*` | 寤惰繜浠诲姟鍚?Tier 鎵ц閲忋€佽秴鏃剁巼 |
| ADR-022 | `omplatform_gray_*` | 鐏板害璇锋眰閲忋€佺増鏈竴鑷存€с€侀檷绾ф鏁?|
| ADR-023 | `omplatform_masking_*` | 鑴辨晱鎵ц娆℃暟銆佺啍鏂鏁?|
| ADR-024 | `omplatform_sql_*` | 鎱?SQL 鏁伴噺銆丼QL 瀹℃煡闃绘柇娆℃暟銆佺储寮曞啑浣欑巼 |
| ADR-025 | `omplatform_openapi_*` | 澶栭儴 API 璋冪敤閲忋€侀厤棰濅娇鐢ㄧ巼銆佺鍚嶅け璐ユ鏁?|

### 9. 鏃ュ織閲囨牱涓庨檷绾?
涓洪槻姝㈡瀬绔祦閲忎笅鐨勬棩蹇楁椽娴侊紝Gateway 灞傚疄鐜版棩蹇楅噰鏍凤細

```java
@Component
public class LogSamplingFilter implements GlobalFilter {
    
    private static final double SAMPLE_RATE = 0.1;  // 鐢熶骇鐜 10%
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 閿欒鏃ュ織 100% 璁板綍
        // INFO 鏃ュ織鎸夐噰鏍风巼璁板綍
        if (ThreadLocalRandom.current().nextDouble() > SAMPLE_RATE) {
            MDC.put("sampled", "false");
        }
        return chain.filter(exchange);
    }
}
```

---

## 澶囬€夋柟妗堣瘎浼?
### 鏃ュ織鑱氬悎鏂规

| 缁村害 | Loki锛堥€変腑锛?| ELK | 闃块噷浜?SLS |
|------|------------|-----|-----------|
| 杩愮淮鎴愭湰 | 鉁?杞婚噺 | 鉂?ES 闆嗙兢缁存姢閲?| 鉁?鎵樼 |
| 瀛樺偍鎴愭湰 | 鉁?瀵硅薄瀛樺偍 | 鈿狅笍 闇€ ES 鑺傜偣 | 鉁?鎸夐噺浠樿垂 |
| 鏌ヨ閫熷害 | 鈿狅笍 澶ф椂闂磋寖鍥存參 | 鉁?ES 鍏ㄦ枃绱㈠紩蹇?| 鉁?蹇?|
| Grafana 闆嗘垚 | 鉁?鍘熺敓 | 鈻?闇€閰嶇疆 | 鉂?鐙珛 UI |
| 绉佹湁鍖栭儴缃?| 鉁?鍙互 | 鉁?鍙互 | 鉂?渚濊禆浜戝巶鍟?|

**缁撹**锛歀oki + Grafana 鍘熺敓闆嗘垚锛岃繍缁存垚鏈渶浣庯紝绗﹀悎绉佹湁鍖栭儴缃查渶姹傘€?
### 閲囨牱绛栫暐

| 缁村害 | Head-based锛堥€変腑锛?| Tail-based | Dynamic |
|------|------------------|-----------|---------|
| 瀹炵幇澶嶆潅搴?| 鉁?浣?| 鉂?楂?| 鉂?楂?|
| 璧勬簮娑堣€?| 鉁?浣庯紙5% 閲囨牱锛?| 鈿狅笍 闇€缂撳啿鎵€鏈?Span | 鈿狅笍 瑙勫垯寮曟搸 |
| 鎱?trace 鎹曡幏 | 鉂?姒傜巼鎬?| 鉁?100% 鎹曡幏鎱?trace | 鉁?鎸夎鍒?|
| 鎺ㄨ崘鍦烘櫙 | 甯歌鐩戞帶 | 瀹炴椂璐ㄩ噺鐩戞帶 | 鑷€傚簲 |

**缁撹**锛欻ead-based锛?%锛? 鎱?trace 灏鹃儴鑷姩淇濈暀锛? 1s锛夛紝骞宠　鎴愭湰涓庤川閲忋€?
---

## 瀹炴柦璁″垝

| 闃舵 | 鏍稿績浠诲姟 | 宸ユ椂 | 浜у嚭 |
|------|---------|------|------|
| P1 鏃ュ織鍩虹璁炬柦 | JSON 鏃ュ織妯″紡 + MDC Filter 娉ㄥ叆 + Loki 閮ㄧ讲 | 2d | 鎵€鏈夋湇鍔?JSON 鏃ュ織 + MDC(traceId) |
| P2 鎸囨爣瑙勮寖钀藉湴 | Micrometer + 鍛藉悕瑙勮寖 + 鍏叡鏍囩 + 棣栨壒鎸囨爣 | 1.5d | 姣忎釜鏈嶅姟鍚姩 10+ 鏍稿績鎸囨爣 |
| P3 閾捐矾閲囨牱 | SkyWalking 閲囨牱閰嶇疆 + 鎱?trace 鑷姩淇濈暀 | 0.5d | 5% 閲囨牱 + 鎱?trace 淇濈暀 |
| P4 SLI/SLO | 鏍稿績 SLI 瀹氫箟 + 鐕冪儳鐜囧憡璀﹁鍒?+ 鐪嬫澘 | 2d | 8 涓牳蹇?SLI + 鐕冪儳鐜囧憡璀?|
| P5 鍛婅浣撶郴 | AlertManager 璺敱 + 鍒嗙骇 + 鍗囩骇绛栫暐 | 1d | P0/P1/P2/P3 鍛婅鍏ㄩ摼璺?|
| P6 Dashboard 鍒嗗眰 | 4 灞?Grafana 鏂囦欢澶?+ 棰勭疆鐪嬫澘妯℃澘 | 1d | 涓氬姟/鏈嶅姟/涓棿浠?SLI 4 灞傜湅鏉?|

**鍚堣**锛? 浜哄ぉ

---

## 涓婄嚎妫€鏌ユ竻鍗?
- [ ] Logback JSON 甯冨眬閰嶇疆鍔犺浇楠岃瘉锛堟墍鏈夋湇鍔★級
- [ ] MDC 鑷姩娉ㄥ叆楠岃瘉锛圵eb + Dubbo 閾捐矾锛?- [ ] traceId 鍦ㄦ棩蹇椾腑姝ｇ‘鏄剧ず
- [ ] Prometheus 鎸囨爣娉ㄥ唽 + `omplatform_` 鍓嶇紑 + 鍏叡鏍囩
- [ ] SkyWalking 5% 澶撮儴閲囨牱閰嶇疆鐢熸晥
- [ ] 鎱?trace锛? 1s锛夊熬閮ㄨ嚜鍔ㄤ繚鐣欓獙璇?- [ ] Grafana + Loki 鏁版嵁婧愰厤缃墦閫?- [ ] 鏍稿績 SLI 鏌ヨ璇彞鏍￠獙 + SLO 鐩爣鍚堢悊鎬х‘璁?- [ ] AlertManager 璺敱楠岃瘉锛圥0 鐢佃瘽銆丳1 缇ゆ秷鎭級
- [ ] 鏃ュ織閲囨牱闄嶇骇楠岃瘉锛?00 TPS 涓嬫棩蹇楅噺鍙帶锛?- [ ] Loki 瀛樺偍閰嶇疆 + 淇濈暀绛栫暐锛?0d 鐑瓨 + 90d 鍐峰瓨锛?- [ ] 鐏板害鏈熼棿鏃х増鏈棩蹇楁牸寮忓吋瀹规€?
---

## 涓庣幇鏈夋枃妗ｇ殑鍏宠仈

- **ADR-018**锛氳ˉ鍏呯殑鍙娴嬫€ф灦鏋勬槸 ADR-018 2 涓笓椤圭湅鏉跨殑涓婂眰妗嗘灦
- **ADR-026**锛歁DC 涓?`userId` 鏉ヨ嚜 AuthContext锛圓DR-026 瀹氫箟锛?- **ADR-024**锛歋QL 鍩虹嚎鎬ц兘鎸囨爣鎺ュ叆缁熶竴鐨?`omplatform_sql_*` 鍛藉悕浣撶郴
- **ADR-012/013/014**锛氬悇 ADR 鐨勬寚鏍囩粺涓€绾冲叆 `omplatform_` 鍓嶇紑瑙勮寖
- **ADR-020**锛歋aga 鎵ц鐘舵€佺殑 SLI 瀹氫箟锛圫aga 鎴愬姛鐜囥€佽ˉ鍋挎垚鍔熺巼锛?- **cicd-pipeline.md**锛歅RE 闃舵銆屾€ц兘鍩虹嚎瀵规瘮銆嶇殑鏁版嵁鏉ユ簮鏄繖閲岀殑 SLI 鎸囨爣
