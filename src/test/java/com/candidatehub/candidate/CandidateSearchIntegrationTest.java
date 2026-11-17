package com.candidatehub.candidate;

import com.candidatehub.common.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real queries (Hibernate + Flyway schema + H2) to prove the search/filter logic. */
@SpringBootTest
@ActiveProfiles("test")
class CandidateSearchIntegrationTest {

    @Autowired
    CandidateService service;
    @Autowired
    CandidateRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        service.create(new CandidateRequest("Jane", "Doe", "jane@acme.com", null, "Backend Engineer", "Acme",
                "Gurugram", 5, CandidateStatus.NEW, Set.of("Java", "Kafka"), null));
        service.create(new CandidateRequest("John", "Smith", "john@globex.com", null, "Frontend Engineer", "Globex",
                "Bangalore", 2, CandidateStatus.SCREENING, Set.of("Angular", "TypeScript"), null));
        service.create(new CandidateRequest("Priya", "Sharma", "priya@acme.com", null, "Tech Lead", "Acme",
                "Gurugram", 9, CandidateStatus.INTERVIEWING, Set.of("Java", "Spring", "Kafka"), null));
        service.create(new CandidateRequest("Rahul", "100%_Sure", "rahul@initech.com", null, "QA Engineer", "Initech",
                "Pune", 3, CandidateStatus.REJECTED, Set.of(), null));
    }

    private static CandidateSearchCriteria criteria(String q, List<CandidateStatus> statuses, List<String> skills,
                                                    String location, String company, Integer min, Integer max) {
        return new CandidateSearchCriteria(q, statuses, skills, location, company, min, max);
    }

    private List<String> firstNames(CandidateSearchCriteria c) {
        PageResponse<CandidateResponse> page = service.search(c, PageRequest.of(0, 20, Sort.by("firstName")));
        return page.content().stream().map(CandidateResponse::firstName).toList();
    }

    @Test
    void noCriteriaReturnsEverything() {
        assertThat(firstNames(criteria(null, null, null, null, null, null, null)))
                .containsExactly("Jane", "John", "Priya", "Rahul");
    }

    @Test
    void freeTextMatchesAcrossFieldsCaseInsensitively() {
        assertThat(firstNames(criteria("ACME", null, null, null, null, null, null))).containsExactly("Jane", "Priya");
        assertThat(firstNames(criteria("jane doe", null, null, null, null, null, null))).containsExactly("Jane");
        assertThat(firstNames(criteria("tech lead", null, null, null, null, null, null))).containsExactly("Priya");
    }

    @Test
    void likeWildcardsInUserInputAreTreatedLiterally() {
        assertThat(firstNames(criteria("%", null, null, null, null, null, null))).containsExactly("Rahul");
        assertThat(firstNames(criteria("_", null, null, null, null, null, null))).containsExactly("Rahul");
    }

    @Test
    void statusFilterIsAnyOf() {
        assertThat(firstNames(criteria(null, List.of(CandidateStatus.NEW, CandidateStatus.REJECTED),
                null, null, null, null, null))).containsExactly("Jane", "Rahul");
    }

    @Test
    void skillsFilterRequiresAllSkills() {
        assertThat(firstNames(criteria(null, null, List.of("java"), null, null, null, null)))
                .containsExactly("Jane", "Priya");
        assertThat(firstNames(criteria(null, null, List.of("JAVA", "spring"), null, null, null, null)))
                .containsExactly("Priya");
        assertThat(firstNames(criteria(null, null, List.of("java", "angular"), null, null, null, null))).isEmpty();
    }

    @Test
    void experienceRangeIsInclusive() {
        assertThat(firstNames(criteria(null, null, null, null, null, 3, 5))).containsExactly("Jane", "Rahul");
        assertThat(firstNames(criteria(null, null, null, null, null, 9, null))).containsExactly("Priya");
    }

    @Test
    void multipleCriteriaAreCombinedWithAnd() {
        assertThat(firstNames(criteria(null, List.of(CandidateStatus.NEW, CandidateStatus.INTERVIEWING),
                List.of("kafka"), "gurugram", "acme", 6, null))).containsExactly("Priya");
    }

    @Test
    void paginationAndSortingWork() {
        PageResponse<CandidateResponse> page = service.search(
                criteria(null, null, null, null, null, null, null),
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "yearsOfExperience")));

        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.content()).extracting(CandidateResponse::firstName).containsExactly("Rahul", "John");
    }
}
