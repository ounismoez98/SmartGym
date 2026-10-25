package com.smartgym.membership.api;

import java.util.UUID;

import com.smartgym.membership.payment.PaymentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/membership/members/{memberId}/payments")
public class PaymentTotalController {
    private final PaymentService paymentService;

    public PaymentTotalController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/total")
    @PreAuthorize("hasRole('ADMIN') or @accessChecker.isOwner(#p0, authentication)")
    public PaymentTotalResponse total(@PathVariable UUID memberId) {
        return paymentService.totalForMember(memberId);
    }
}
