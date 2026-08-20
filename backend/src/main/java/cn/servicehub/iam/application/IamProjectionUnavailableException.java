package cn.servicehub.iam.application;

/** Raised when an authenticated IAM subject has no active local projection yet. */
public class IamProjectionUnavailableException extends RuntimeException {
    public IamProjectionUnavailableException() {
        super("Authenticated IAM subject has no active platform projection");
    }
}
