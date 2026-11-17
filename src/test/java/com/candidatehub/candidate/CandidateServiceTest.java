package com.candidatehub.candidate;

import com.candidatehub.common.ConflictException;
import com.candidatehub.common.InvalidRequestException;
import com.candidatehub.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Business-rule tests with the repository mocked: no Spring context, runs in milliseconds. */
@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock
    CandidateRepository repository;

    @InjectMocks
    CandidateService service;

    private static CandidateRequest request(String email, Long version) {
        return new CandidateRequest("Jane", "Doe", email, null, null, null, null, 4,
                null, Set.of("java"), version);
    }

    private static CandidateSearchCriteria criteria(Integer min, Integer max) {
        return new CandidateSearchCriteria(null, null, null, null, null, min, max);
    }

    @Test
    void createRejectsDuplicateEmailCaseInsensitively() {
        when(repository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("Jane@Example.com", null)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteThrowsNotFoundForUnknownId() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete((Candidate) any());
    }

    @Test
    void updateWithStaleVersionIsRejected() {
        Candidate existing = new Candidate(); // version defaults to 0
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(1L, request("jane@example.com", 7L)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsEmailBelongingToAnotherCandidate() {
        when(repository.findById(1L)).thenReturn(Optional.of(new Candidate()));
        when(repository.existsByEmailAndIdNot("taken@example.com", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, request("taken@example.com", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void searchRejectsInvertedExperienceRange() {
        assertThatThrownBy(() -> service.search(criteria(10, 2), PageRequest.of(0, 10)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void searchRejectsNegativeExperience() {
        assertThatThrownBy(() -> service.search(criteria(-1, null), PageRequest.of(0, 10)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void searchRejectsSortOnNonWhitelistedField() {
        PageRequest page = PageRequest.of(0, 10, Sort.by("skills"));

        assertThatThrownBy(() -> service.search(criteria(null, null), page))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cannot sort by 'skills'");
    }
}
