package cn.servicehub.localauth.web;

import cn.servicehub.iam.application.IamProjectionService;
import cn.servicehub.iam.web.CurrentUserResponse;
import cn.servicehub.localauth.application.LocalAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class LocalAuthController {
    private final LocalAuthenticationService authentication;private final IamProjectionService projections;private final HttpSessionSecurityContextRepository contexts=new HttpSessionSecurityContextRepository();
    public LocalAuthController(LocalAuthenticationService authentication,IamProjectionService projections){this.authentication=authentication;this.projections=projections;}
    @PostMapping("/login") public CurrentUserResponse login(@Valid @RequestBody LoginRequest body,HttpServletRequest request,HttpServletResponse response){var verified=authentication.authenticate(body.loginName(),body.password());HttpSession prior=request.getSession(false);if(prior!=null)request.changeSessionId();var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(verified);SecurityContextHolder.setContext(context);contexts.saveContext(context,request,response);return CurrentUserResponse.from(projections.currentUserProjection());}
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(HttpServletRequest request,HttpServletResponse response){HttpSession session=request.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();contexts.saveContext(SecurityContextHolder.createEmptyContext(),request,response);}
    public record LoginRequest(@NotBlank @Size(max=128) String loginName,@NotBlank @Size(max=128) String password){}
}
