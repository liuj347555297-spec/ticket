package cn.servicehub.web;

import cn.servicehub.iam.application.IamProjectionUnavailableException;
import cn.servicehub.catalog.application.CatalogValidationException;
import cn.servicehub.catalog.config.FormConfigurationConflictException;
import cn.servicehub.catalog.config.FormConfigurationValidationException;
import cn.servicehub.ticket.application.TicketNotFoundException;
import cn.servicehub.ticket.domain.IdempotencyConflictException;
import cn.servicehub.workflow.application.WorkflowConflictException;
import cn.servicehub.workflow.application.WorkflowExecutionUnavailableException;
import cn.servicehub.workflow.application.WorkflowStateException;
import cn.servicehub.access.application.BackofficeAccessConflictException;
import cn.servicehub.attachment.application.AttachmentNotFoundException;
import cn.servicehub.attachment.application.AttachmentValidationException;
import cn.servicehub.knowledge.application.KnowledgeNotFoundException;
import cn.servicehub.knowledge.application.KnowledgeValidationException;
import cn.servicehub.knowledge.application.KnowledgeDraftConflictException;
import cn.servicehub.integration.application.IntegrationSecurityException;
import cn.servicehub.announcement.application.AnnouncementValidationException;
import cn.servicehub.servicesystem.application.ServiceSystemConflictException;
import cn.servicehub.servicesystem.application.ServiceSystemValidationException;
import cn.servicehub.localauth.application.LocalAccountConflictException;
import cn.servicehub.localauth.application.LocalAccountNotFoundException;
import cn.servicehub.localauth.application.LocalAuthenticationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class})
    ResponseEntity<ApiError> validation(Exception ignored, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request", request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableRequest(HttpMessageNotReadableException ignored, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request", request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> illegalArgument(IllegalArgumentException ignored, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request", request));
    }

    @ExceptionHandler(CatalogValidationException.class)
    ResponseEntity<ApiError> catalogValidation(CatalogValidationException ignored, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "SERVICE_CATALOG_INVALID", "Service catalog input is invalid", request));
    }

    @ExceptionHandler(FormConfigurationValidationException.class)
    ResponseEntity<ApiError> formConfigurationValidation(FormConfigurationValidationException ignored, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "FORM_CONFIGURATION_INVALID", "Form configuration is invalid", request));
    }

    @ExceptionHandler(cn.servicehub.designer.StudioConflictException.class)
    ResponseEntity<ApiError> studioConflict(cn.servicehub.designer.StudioConflictException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT, "DESIGN_STUDIO_CONFLICT", "Design draft changed; reload and compare before saving", request));
    }

    @ExceptionHandler(FormConfigurationConflictException.class)
    ResponseEntity<ApiError> formConfigurationConflict(FormConfigurationConflictException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT, "FORM_CONFIGURATION_CONFLICT", "Form configuration version conflicts with current state", request));
    }

    @ExceptionHandler(ServiceSystemValidationException.class)
    ResponseEntity<ApiError> serviceSystemValidation(ServiceSystemValidationException ignored, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "SERVICE_SYSTEM_INVALID", "Service system input is invalid", request));
    }

    @ExceptionHandler(ServiceSystemConflictException.class)
    ResponseEntity<ApiError> serviceSystemConflict(ServiceSystemConflictException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT, "SERVICE_SYSTEM_CONFLICT", "Service system version conflicts with current state", request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method is not supported", request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> resourceNotFound(NoResourceFoundException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource was not found", request));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ApiError> idempotencyConflict(IdempotencyConflictException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key conflicts with a prior request", request));
    }

    @ExceptionHandler({WorkflowConflictException.class, WorkflowStateException.class, BackofficeAccessConflictException.class})
    ResponseEntity<ApiError> workflowConflict(RuntimeException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(error(HttpStatus.CONFLICT, "WORKFLOW_CONFLICT", "Workflow action conflicts with current state", request));
    }

    @ExceptionHandler(LocalAccountConflictException.class)
    ResponseEntity<ApiError> localAccountConflict(LocalAccountConflictException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT, "LOCAL_ACCOUNT_CONFLICT", "Local account changed; reload before retrying", request));
    }

    @ExceptionHandler(LocalAccountNotFoundException.class)
    ResponseEntity<ApiError> localAccountNotFound(LocalAccountNotFoundException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource was not found", request));
    }

    @ExceptionHandler(LocalAuthenticationFailedException.class)
    ResponseEntity<ApiError> localAuthenticationFailed(LocalAuthenticationFailedException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Invalid login name or password", request));
    }

    @ExceptionHandler(WorkflowExecutionUnavailableException.class)
    ResponseEntity<ApiError> workflowExecutionUnavailable(WorkflowExecutionUnavailableException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Workflow execution is temporarily unavailable", request));
    }

    @ExceptionHandler({TicketNotFoundException.class, AttachmentNotFoundException.class, KnowledgeNotFoundException.class})
    ResponseEntity<ApiError> ticketNotFound(RuntimeException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource was not found", request));
    }

    @ExceptionHandler({AttachmentValidationException.class, KnowledgeValidationException.class, AnnouncementValidationException.class})
    ResponseEntity<ApiError> attachmentRejected(RuntimeException ignored, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "CONTENT_REJECTED", "Content is not accepted", request));
    }

    @ExceptionHandler(KnowledgeDraftConflictException.class)
    ResponseEntity<ApiError> knowledgeDraftConflict(KnowledgeDraftConflictException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(error(HttpStatus.CONFLICT, "KNOWLEDGE_DRAFT_CONFLICT", "Knowledge draft changed; reload before saving", request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException ignored, HttpServletRequest request) {
        // Read-side ticket object endpoints deliberately collapse "missing" and "not allowed" so
        // sequential ticket identifiers cannot be used as an existence oracle. Mutation attempts
        // remain 403 because callers need an explicit authorization failure and already know the ID.
        if (("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
            && request.getRequestURI().matches("^/api/v1/tickets/TKT-[0-9]{8}-[0-9]{6}(?:/.*)?$")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource was not found", request));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(error(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not authorized for this operation", request));
    }

    @ExceptionHandler(IntegrationSecurityException.class)
    ResponseEntity<ApiError> integrationRejected(IntegrationSecurityException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(error(HttpStatus.FORBIDDEN, "INTEGRATION_REJECTED", "Integration callback was rejected", request));
    }

    @ExceptionHandler(IamProjectionUnavailableException.class)
    ResponseEntity<ApiError> iamProjectionUnavailable(IamProjectionUnavailableException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(error(HttpStatus.FORBIDDEN, "FORBIDDEN", "The authenticated identity is not available for this platform", request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unhandled(Exception exception, HttpServletRequest request) {
        log.error("Unhandled API error; requestId={}, method={}, path={}",
            request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE), request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", request));
    }

    private ApiError error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return new ApiError(Instant.now(), status.value(), code, message,
            (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
    }
}
