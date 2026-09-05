package cn.servicehub.localauth.domain;

import java.time.Instant;
import java.util.Optional;

public interface LocalAccountRepository {
    Optional<LocalAccount> findById(String id);
    Optional<LocalAccount> findByNormalizedLoginName(String normalizedLoginName);
    long count();
    LocalAccountPage search(int page, int pageSize, String query, String status, Instant now);
    LocalAccount insert(LocalAccount account);
    LocalAccount update(LocalAccount account, long expectedVersion);
}
