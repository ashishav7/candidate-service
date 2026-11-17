package com.candidatehub.candidate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Payload for create (POST) and full-replace update (PUT).
 *
 * @param status  optional: defaults to NEW on create, left unchanged on update when null
 * @param version optional on update: if supplied it must match the stored version (optimistic locking)
 */
public record CandidateRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @Pattern(regexp = "^\\+?[0-9 ()\\-]{7,20}$", message = "must be a valid phone number")
        String phone,
        @Size(max = 150) String currentTitle,
        @Size(max = 150) String currentCompany,
        @Size(max = 150) String location,
        @NotNull @Min(0) @Max(60) Integer yearsOfExperience,
        CandidateStatus status,
        @Size(max = 50) Set<@NotBlank @Size(max = 50) String> skills,
        Long version) {
}
