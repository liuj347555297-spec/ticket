package cn.servicehub.catalog.application;

/** Intentionally generic: callers cannot use validation errors to enumerate draft catalog configuration. */
public class CatalogValidationException extends RuntimeException {
    public CatalogValidationException() {
        super("Service catalog input is invalid");
    }
}
