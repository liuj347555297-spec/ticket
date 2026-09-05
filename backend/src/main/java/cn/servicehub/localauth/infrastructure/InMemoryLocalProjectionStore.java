package cn.servicehub.localauth.infrastructure;

import cn.servicehub.iam.domain.IamUserProjection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!mysql")
public class InMemoryLocalProjectionStore {
    private final Map<String, IamUserProjection> values = new ConcurrentHashMap<>();
    public Optional<IamUserProjection> findActive(String id) { return Optional.ofNullable(values.get(id)).filter(IamUserProjection::active); }
    public void put(IamUserProjection value) { values.put(value.iamUserId(), value); }
}
