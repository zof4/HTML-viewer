package com.example.htmlviewer.controller;

import com.example.htmlviewer.model.DocumentModels;
import com.example.htmlviewer.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DocumentService.VersionConflictException.class)
    public ResponseEntity<DocumentModels.ConflictResponse> handleVersionConflict(DocumentService.VersionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new DocumentModels.ConflictResponse("VERSION_CONFLICT", ex.getCurrentVersion(), ex.getCurrentHtml()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "VALIDATION_ERROR", "message", "Invalid request payload"));
    }
}
