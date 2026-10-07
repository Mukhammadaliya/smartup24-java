package uz.greenwhite.sb.uzum.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import uz.greenwhite.sb.uzum.exception.GatewayException;
import uz.greenwhite.sb.uzum.geteway.UzumGeteway;
import uz.greenwhite.sb.uzum.response.ErrorResponse;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/uzum_service")
@RequiredArgsConstructor
public class UzumServiceController {

    private final UzumGeteway uzumGeteway;

    @ExceptionHandler
    public ResponseEntity<List<ErrorResponse>> handleGatewayException(GatewayException e, HttpServletRequest request) {
        List<ErrorResponse> errorResponseList = List.of(new ErrorResponse(e.getCode(), e.getMessage()));

        String contentType = request.getMethod().equals("POST") ? "application/vnd.eda.picker.errors.v1+json" : "application/json";

        return ResponseEntity
                .status(HttpStatus.valueOf(e.getCode()))
                .header("Content-Type", contentType)
                .body(errorResponseList);
    }

    @ExceptionHandler
    public ResponseEntity<List<ErrorResponse>> handleMissingRequestHeaderException(MissingRequestHeaderException e, HttpServletRequest request) {
        List<ErrorResponse> errorResponseList = List.of(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), e.getMessage()));

        String contentType = request.getMethod().equals("POST") ? "application/vnd.eda.picker.errors.v1+json" : "application/json";

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .header("Content-Type", contentType)
                .body(errorResponseList);
    }

    @ExceptionHandler
    public ResponseEntity<List<ErrorResponse>> handleException(Exception e, HttpServletRequest request) {
        List<ErrorResponse> errorResponseList = List.of(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage()));

        String contentType = request.getMethod().equals("POST") ? "application/vnd.eda.picker.errors.v1+json" : "application/json";

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", contentType)
                .body(errorResponseList);
    }

    @PostMapping(
            produces = "application/json",
            path = {"/check", "/create", "/confirm", "/reverse", "/status"}
    )
    @ResponseStatus(HttpStatus.CREATED)
    public String handleUzumRequest(
            HttpServletRequest request,
            @RequestHeader(name = "Authorization") String authHeader,
            @RequestBody String body) throws IOException {
        return uzumGeteway.forward(request, authHeader, body);
    }
}
