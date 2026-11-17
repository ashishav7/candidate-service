package com.candidatehub.candidate;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Mapping + normalisation (trim, lower-case email/skills) in one place so it is unit-testable. */
final class CandidateMapper {

    private CandidateMapper() {
    }

    static void apply(Candidate target, CandidateRequest r) {
        target.setFirstName(r.firstName().trim());
        target.setLastName(r.lastName().trim());
        target.setEmail(normalizeEmail(r.email()));
        target.setPhone(blankToNull(r.phone()));
        target.setCurrentTitle(blankToNull(r.currentTitle()));
        target.setCurrentCompany(blankToNull(r.currentCompany()));
        target.setLocation(blankToNull(r.location()));
        target.setYearsOfExperience(r.yearsOfExperience());
        if (r.status() != null) {
            target.setStatus(r.status());
        }
        target.setSkills(normalizeSkills(r.skills()));
    }

    static CandidateResponse toResponse(Candidate c) {
        return new CandidateResponse(
                c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(), c.getPhone(),
                c.getCurrentTitle(), c.getCurrentCompany(), c.getLocation(),
                c.getYearsOfExperience(), c.getStatus(),
                c.getSkills().stream().sorted().toList(),
                c.getVersion(), c.getCreatedAt(), c.getUpdatedAt());
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeSkill(String skill) {
        return skill.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalizeSkills(Set<String> skills) {
        if (skills == null) {
            return Set.of();
        }
        return skills.stream().map(CandidateMapper::normalizeSkill).collect(Collectors.toSet());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
