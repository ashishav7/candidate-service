package com.candidatehub.candidate;

import java.util.List;

/**
 * All filters are optional and combined with AND.
 *
 * @param q             free-text, case-insensitive "contains" over name, email, title, company
 * @param statuses      candidate matches if its status is ANY of these
 * @param skills        candidate must have ALL of these skills
 * @param location      case-insensitive "contains"
 * @param company       case-insensitive "contains"
 * @param minExperience inclusive lower bound (years)
 * @param maxExperience inclusive upper bound (years)
 */
public record CandidateSearchCriteria(
        String q,
        List<CandidateStatus> statuses,
        List<String> skills,
        String location,
        String company,
        Integer minExperience,
        Integer maxExperience) {
}
