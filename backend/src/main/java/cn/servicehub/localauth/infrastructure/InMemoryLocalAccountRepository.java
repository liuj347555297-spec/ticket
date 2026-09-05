package cn.servicehub.localauth.infrastructure;

import cn.servicehub.localauth.application.LocalAccountConflictException;
import cn.servicehub.localauth.domain.LocalAccount;
import cn.servicehub.localauth.domain.LocalAccountPage;
import cn.servicehub.localauth.domain.LocalAccountRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryLocalAccountRepository implements LocalAccountRepository {
    private final Map<String, LocalAccount> values = new ConcurrentHashMap<>();

    @Override public Optional<LocalAccount> findById(String id) { return Optional.ofNullable(values.get(id)); }
    @Override public Optional<LocalAccount> findByNormalizedLoginName(String login) {
        return values.values().stream().filter(value -> value.normalizedLoginName().equals(login)).findFirst();
    }
    @Override public long count() { return values.size(); }
    @Override public LocalAccountPage search(int page, int pageSize, String query, String status, Instant now) {
        String q = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        var matched = values.values().stream()
            .filter(value -> q.isBlank() || value.normalizedLoginName().contains(q) || value.displayName().toLowerCase(java.util.Locale.ROOT).contains(q))
            .filter(value -> matchesStatus(value, status, now))
            .sorted(Comparator.comparing(LocalAccount::updatedAt).reversed().thenComparing(LocalAccount::id)).toList();
        int from = Math.min((page - 1) * pageSize, matched.size());
        int to = Math.min(from + pageSize, matched.size());
        return new LocalAccountPage(matched.subList(from, to), page, pageSize, matched.size());
    }
    @Override public synchronized LocalAccount insert(LocalAccount account) {
        if (findByNormalizedLoginName(account.normalizedLoginName()).isPresent() || values.putIfAbsent(account.id(), account) != null) {
            throw new LocalAccountConflictException();
        }
        return account;
    }
    @Override public synchronized LocalAccount update(LocalAccount account, long expectedVersion) {
        LocalAccount current = values.get(account.id());
        if (current == null || current.version() != expectedVersion) throw new LocalAccountConflictException();
        if (values.values().stream().anyMatch(value -> !value.id().equals(account.id()) && value.normalizedLoginName().equals(account.normalizedLoginName()))) {
            throw new LocalAccountConflictException();
        }
        values.put(account.id(), account);
        return account;
    }
    private boolean matchesStatus(LocalAccount value, String status, Instant now) {
        if (status == null || status.isBlank() || "ALL".equals(status)) return true;
        return switch (status) {
            case "ACTIVE" -> value.enabled() && (value.lockedUntil() == null || !value.lockedUntil().isAfter(now));
            case "LOCKED" -> value.enabled() && value.lockedUntil() != null && value.lockedUntil().isAfter(now);
            case "DISABLED" -> !value.enabled();
            default -> false;
        };
    }
}
