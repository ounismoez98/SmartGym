package com.smartgym.membership.profile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Profile("dev")
@RestController
@RequestMapping("/api/dev")
public class DevProvisioningController {
    private final ProvisioningService provisioningService;
    private final byte[] provisioningToken;

    public DevProvisioningController(
            ProvisioningService provisioningService,
            @Value("${app.provisioning.token}") String provisioningToken) {
        this.provisioningService = provisioningService;
        this.provisioningToken = provisioningToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/provision")
    public ProvisioningService.ProvisioningResult provision(
            @RequestHeader("X-Provisioning-Key") String suppliedToken,
            @Valid @RequestBody ProvisioningRequest request) {
        if (provisioningToken.length == 0 || !MessageDigest.isEqual(
                provisioningToken, suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid provisioning key.");
        }
        try {
            return provisioningService.provision(request);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
