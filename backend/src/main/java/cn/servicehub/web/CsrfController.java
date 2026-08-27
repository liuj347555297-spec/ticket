package cn.servicehub.web;

import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Explicit SPA bootstrap endpoint. The token remains a same-origin double-submit value, never an IAM credential. */
@RestController
@RequestMapping("/api/v1")
public class CsrfController {
    @GetMapping("/csrf")
    Map<String, String> csrf(@RequestAttribute(name = "org.springframework.security.web.csrf.CsrfToken") CsrfToken token) {
        return Map.of("token", token.getToken());
    }
}
