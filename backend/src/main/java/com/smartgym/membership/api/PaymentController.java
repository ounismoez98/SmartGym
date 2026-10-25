package com.smartgym.membership.api;

import java.time.Instant;
import java.util.UUID;

import com.smartgym.membership.payment.Payment;
import com.smartgym.membership.payment.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/membership/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(
            @Valid @RequestBody PaymentCreateRequest request,
            Authentication authentication) {
        return paymentService.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MEMBER')")
    public PaymentPageResponse list(
            @RequestParam(required = false) UUID memberId,
            @RequestParam(required = false) Payment.Status status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return paymentService.list(memberId, status, from, to, page, size, authentication);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @accessChecker.isPaymentOwner(#p0, authentication)")
    public PaymentResponse get(@PathVariable UUID id) {
        return paymentService.get(id);
    }

    @PostMapping("/{id}/corrections")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse correct(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentCorrectionRequest request,
            Authentication authentication) {
        return paymentService.correct(id, request, authentication);
    }
}
