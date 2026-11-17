package com.candidatehub.candidate;

import java.time.Instant;
import java.util.List;

public record CandidateResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String currentTitle,
        String currentCompany,
        String location,
        int yearsOfExperience,
        CandidateStatus status,
        List<String> skills,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
