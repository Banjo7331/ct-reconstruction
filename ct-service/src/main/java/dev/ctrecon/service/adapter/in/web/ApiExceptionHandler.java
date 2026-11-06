package dev.ctrecon.service.adapter.in.web;

import dev.ctrecon.core.application.DatasetNotFoundException;
import dev.ctrecon.core.application.ImageNotAvailableException;
import dev.ctrecon.core.application.ReconstructionNotFoundException;
import dev.ctrecon.core.application.port.out.QueueFullException;
import dev.ctrecon.core.domain.IllegalReconstructionStateException;
import dev.ctrecon.core.domain.InvalidReconstructionParametersException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static final String RETRY_AFTER_SECONDS = "30";

    @ExceptionHandler(InvalidReconstructionParametersException.class)
    public ProblemDetail invalidParameters(InvalidReconstructionParametersException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid reconstruction parameters",
                exception.getMessage());
        problem.setProperty("violations", exception.violations());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", exception.getMessage());
    }

    @ExceptionHandler({ReconstructionNotFoundException.class, DatasetNotFoundException.class})
    public ProblemDetail notFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, "Not found", exception.getMessage());
    }

    @ExceptionHandler({IllegalReconstructionStateException.class, ImageNotAvailableException.class})
    public ProblemDetail conflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Conflict with the current state", exception.getMessage());
    }

    @ExceptionHandler(QueueFullException.class)
    public ResponseEntity<ProblemDetail> queueFull(QueueFullException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "Reconstruction queue is full", exception.getMessage()));
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
