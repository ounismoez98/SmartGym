package com.smartgym.courses;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class Module2Exception extends ResponseStatusException {
    public Module2Exception(HttpStatus status, String reason) {
        super(status, reason);
    }
}
