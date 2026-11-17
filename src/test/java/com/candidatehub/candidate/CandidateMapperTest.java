package com.candidatehub.candidate;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateMapperTest {

    private static CandidateRequest request(String email, Set<String> skills, CandidateStatus status) {
        return new CandidateRequest("  Jane ", " Doe", email, "  ", " Engineer ", null, "  ", 5, status, skills, null);
    }

    @Test
    void normalisesEmailSkillsAndBlankFields() {
        Candidate c = new Candidate();
        CandidateMapper.apply(c, request("  Jane.Doe@Example.COM ", Set.of(" Java ", "KAFKA"), null));

        assertThat(c.getFirstName()).isEqualTo("Jane");
        assertThat(c.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(c.getPhone()).isNull();
        assertThat(c.getLocation()).isNull();
        assertThat(c.getCurrentTitle()).isEqualTo("Engineer");
        assertThat(c.getSkills()).containsExactlyInAnyOrder("java", "kafka");
    }

    @Test
    void statusDefaultsToNewAndIsOnlyOverwrittenWhenSupplied() {
        Candidate c = new Candidate();
        CandidateMapper.apply(c, request("a@b.com", null, null));
        assertThat(c.getStatus()).isEqualTo(CandidateStatus.NEW);

        CandidateMapper.apply(c, request("a@b.com", null, CandidateStatus.HIRED));
        assertThat(c.getStatus()).isEqualTo(CandidateStatus.HIRED);

        CandidateMapper.apply(c, request("a@b.com", null, null));
        assertThat(c.getStatus()).isEqualTo(CandidateStatus.HIRED);
    }

    @Test
    void nullSkillsBecomeEmptySet() {
        Candidate c = new Candidate();
        CandidateMapper.apply(c, request("a@b.com", null, null));
        assertThat(c.getSkills()).isEmpty();
    }
}
