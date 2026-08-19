package cn.servicehub.security;

import java.util.Optional;

public interface CurrentUserProvider {
    Optional<CurrentUser> currentUser();
    CurrentUser requireCurrentUser();
}
