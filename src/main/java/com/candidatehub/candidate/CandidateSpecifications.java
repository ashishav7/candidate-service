package com.candidatehub.candidate;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds the dynamic query from {@link CandidateSearchCriteria}. Everything is parameterised through the
 * JPA Criteria API (no string-concatenated SQL), and LIKE wildcards in user input are escaped.
 */
final class CandidateSpecifications {

    private static final char ESCAPE = '\\';

    private CandidateSpecifications() {
    }

    static Specification<Candidate> from(CandidateSearchCriteria c) {
        List<Specification<Candidate>> specs = new ArrayList<>();

        if (hasText(c.q())) {
            specs.add(freeText(c.q()));
        }
        if (c.statuses() != null && !c.statuses().isEmpty()) {
            specs.add((root, query, cb) -> root.get("status").in(c.statuses()));
        }
        if (hasText(c.location())) {
            specs.add(containsIgnoreCase("location", c.location()));
        }
        if (hasText(c.company())) {
            specs.add(containsIgnoreCase("currentCompany", c.company()));
        }
        if (c.minExperience() != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Integer>get("yearsOfExperience"), c.minExperience()));
        }
        if (c.maxExperience() != null) {
            specs.add((root, query, cb) -> cb.lessThanOrEqualTo(root.<Integer>get("yearsOfExperience"), c.maxExperience()));
        }
        if (c.skills() != null) {
            for (String skill : c.skills()) {
                if (hasText(skill)) {
                    String normalized = CandidateMapper.normalizeSkill(skill);
                    // one "member of" (EXISTS sub-query) per skill => candidate must have ALL skills, no row duplication
                    specs.add((root, query, cb) -> cb.isMember(normalized, root.<Set<String>>get("skills")));
                }
            }
        }
        return Specification.allOf(specs);
    }

    private static Specification<Candidate> freeText(String text) {
        String pattern = likePattern(text);
        return (root, query, cb) -> {
            List<Predicate> any = new ArrayList<>();
            for (String field : List.of("firstName", "lastName", "email", "currentTitle", "currentCompany")) {
                Expression<String> lowered = cb.lower(root.<String>get(field));
                any.add(cb.like(lowered, pattern, ESCAPE));
            }
            // "Jane Doe" style queries: also match against the full name
            Expression<String> fullName = cb.lower(cb.concat(cb.concat(root.<String>get("firstName"), " "), root.<String>get("lastName")));
            any.add(cb.like(fullName, pattern, ESCAPE));
            return cb.or(any.toArray(Predicate[]::new));
        };
    }

    private static Specification<Candidate> containsIgnoreCase(String field, String value) {
        String pattern = likePattern(value);
        return (root, query, cb) -> cb.like(cb.lower(root.<String>get(field)), pattern, ESCAPE);
    }

    private static String likePattern(String raw) {
        String escaped = raw.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
