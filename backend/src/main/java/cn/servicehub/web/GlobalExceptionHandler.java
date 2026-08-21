package cn.servicehub.web;

import cn.servicehub.iam.application.IamProjectionUnavailableException;
import cn.servicehub.catalog.application.CatalogValidationException;
import cn.servicehub.ticket.application.TicketNotFoundException;
import cn.servicehub.ticket.domain.IdempotencyConflictException;
import cn.servicehub.workflow.application.WorkflowConflictException;
import cn.servicehub.workflow.application.WorkflowStateException;
import cn.servicehub.attachment.application.AttachmentNotFoundException;
import cn.servicehub.attachment.application.AttachmentValidationException;
import cn.servicehub.knowledge.application.KnowledgeNotFoundException;
import cn.servicehub.knowledge.application.KnowledgeValidationException;
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

@RestControllerAdvice
public class GlobalExceptionHandler {
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

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method is not supported", request));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ApiError> idempotencyConflict(IdempotencyConflictException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key conflicts with a prior request", request));
    }

    @ExceptionHandler({WorkflowConflictException.class, WorkflowStateException.class})
    ResponseEntity<ApiError> workflowConflict(RuntimeException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(error(HttpStatus.CONFLICT, "WORKFLOW_CONFLICT", "Workflow action conflicts with current state", request));
    }

    @ExceptionHandler({TicketNotFoundException.class, AttachmentNotFoundException.class, KnowledgeNotFoundException.class})
    ResponseEntity<ApiError> ticketNotFound(TicketNotFoundException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource was not found", request));
    }

    @ExceptionHandler({AttachmentValidationException.class, KnowledgeValidationException.class})
    ResponseEntity<ApiError> attachmentRejected(RuntimeException ignored, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "CONTENT_REJECTED", "Content is not accepted", request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(error(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not authorized for this operation", request));
    }

    @ExceptionHandler(IamProjectionUnavailableException.class)
    ResponseEntity<ApiError> iamProjectionUnavailable(IamProjectionUnavailableException ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(error(HttpStatus.FORBIDDEN, "FORBIDDEN", "The authenticated identity is not available for this platform", request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unhandled(Exception ignored, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", request));
    }

    private ApiError error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return new ApiError(Instant.now(), status.value(), code, message,
            (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
    }
}
