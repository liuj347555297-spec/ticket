package cn.servicehub.workflow.team;
import java.nio.charset.StandardCharsets;import java.time.Duration;import java.util.LinkedHashMap;import java.util.Map;import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="servicehub.support-queue.idempotency")
public record SupportQueueIdempotencyProperties(String currentKeyVersion,String keyRing,Duration leaseDuration){
 public SupportQueueIdempotencyProperties{if(currentKeyVersion==null||currentKeyVersion.isBlank()||keyRing==null||leaseDuration==null||leaseDuration.isZero()||leaseDuration.isNegative())throw new IllegalArgumentException("Support queue idempotency configuration is incomplete");Map<String,byte[]>keys=parse(keyRing);if(!keys.containsKey(currentKeyVersion))throw new IllegalArgumentException("Current idempotency key version is unavailable");for(byte[]key:keys.values())if(key.length<32)throw new IllegalArgumentException("Idempotency HMAC keys require at least 32 UTF-8 bytes");}
 public Map<String,byte[]>keys(){return Map.copyOf(parse(keyRing));}
 private static Map<String,byte[]>parse(String raw){Map<String,byte[]>out=new LinkedHashMap<>();for(String entry:raw.split(";")){int p=entry.indexOf('=');if(p<1||p==entry.length()-1)throw new IllegalArgumentException("Idempotency key ring is invalid");String version=entry.substring(0,p).trim();if(out.putIfAbsent(version,entry.substring(p+1).getBytes(StandardCharsets.UTF_8))!=null)throw new IllegalArgumentException("Duplicate idempotency key version");}return out;}
}
