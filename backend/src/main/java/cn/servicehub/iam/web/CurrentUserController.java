package cn.servicehub.iam.web;

import cn.servicehub.iam.application.IamProjectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Returns only the authenticated subject's read-only IAM projection. */
@RestController
@RequestMapping("/api/v1/me")
public class CurrentUserController {
    private final IamProjectionService iamProjectionService;

    public CurrentUserController(IamProjectionService iamProjectionService) {
        this.iamProjectionService = iamProjectionService;
    }

    @GetMapping
    CurrentUserResponse get() {
        return CurrentUserResponse.from(iamProjectionService.currentUserProjection());
    }
}
