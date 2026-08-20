package cn.servicehub.iam.domain;

import java.util.Optional;

/** Query-only port. No caller can create local accounts or alter IAM organization data through it. */
public interface IamUserProjectionRepository {
    Optional<IamUserProjection> findActiveByIamUserId(String iamUserId);
}
