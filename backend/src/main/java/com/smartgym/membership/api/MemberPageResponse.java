package com.smartgym.membership.api;

import java.util.List;

import org.springframework.data.domain.Page;

public record MemberPageResponse(
        List<MemberResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
    public static MemberPageResponse from(Page<MemberResponse> page) {
        return new MemberPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
