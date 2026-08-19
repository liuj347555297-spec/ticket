package cn.servicehub.web;

import cn.servicehub.security.CurrentUserProvider;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Temporary protected endpoint proving the security boundary; not a public health check. */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    private final CurrentUserProvider currentUserProvider;

    public SystemController(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/ping")
    Map<String, String> ping() {
        return Map.of("status", "ok", "user", currentUserProvider.requireCurrentUser().iamUserId());
    }
}
