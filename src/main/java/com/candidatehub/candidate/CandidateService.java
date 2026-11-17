package com.candidatehub.candidate;

import com.candidatehub.common.ConflictException;
import com.candidatehub.common.InvalidRequestException;
import com.candidatehub.common.PageResponse;
import com.candidatehub.common.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class CandidateService {

    private static final Logger log = LoggerFactory.getLogger(CandidateService.class);

    /** Only these fields may be sorted on (keeps clients off un-indexed / internal columns). */
    static final Set<String> SORTABLE = Set.of(
            "id", "firstName", "lastName", "email", "yearsOfExperience", "status", "createdAt", "updatedAt");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final CandidateRepository repository;

    public CandidateService(CandidateRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CandidateResponse create(CandidateRequest request) {
        String email = CandidateMapper.normalizeEmail(request.email());
        if (repository.existsByEmail(email)) {
            throw new ConflictException("A candidate with this email already exists");
        }
        Candidate candidate = new Candidate();
        CandidateMapper.apply(candidate, request);
        // saveAndFlush so a unique-constraint race surfaces here (-> 409) rather than at commit time.
        Candidate saved = repository.saveAndFlush(candidate);
        log.info("Created candidate id={}", saved.getId());
        return CandidateMapper.toResponse(saved);
    }

    public CandidateResponse get(Long id) {
        return CandidateMapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public CandidateResponse update(Long id, CandidateRequest request) {
        Candidate candidate = findOrThrow(id);
        if (request.version() != null && request.version() != candidate.getVersion()) {
            throw new ConflictException("Candidate was modified by someone else (stale version). Reload and retry.");
        }
        String email = CandidateMapper.normalizeEmail(request.email());
        if (repository.existsByEmailAndIdNot(email, id)) {
            throw new ConflictException("A candidate with this email already exists");
        }
        CandidateMapper.apply(candidate, request);
        Candidate saved = repository.saveAndFlush(candidate);
        log.info("Updated candidate id={}", id);
        return CandidateMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Candidate candidate = findOrThrow(id);
        repository.delete(candidate);
        log.info("Deleted candidate id={}", id);
    }

    public PageResponse<CandidateResponse> search(CandidateSearchCriteria criteria, Pageable pageable) {
        validate(criteria);
        Pageable safe = sanitize(pageable);
        log.debug("Searching candidates page={} size={}", safe.getPageNumber(), safe.getPageSize());
        return PageResponse.of(
                repository.findAll(CandidateSpecifications.from(criteria), safe),
                CandidateMapper::toResponse);
    }

    private Candidate findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate " + id + " not found"));
    }

    private static void validate(CandidateSearchCriteria c) {
        if (c.minExperience() != null && c.minExperience() < 0) {
            throw new InvalidRequestException("minExperience must not be negative");
        }
        if (c.maxExperience() != null && c.maxExperience() < 0) {
            throw new InvalidRequestException("maxExperience must not be negative");
        }
        if (c.minExperience() != null && c.maxExperience() != null && c.minExperience() > c.maxExperience()) {
            throw new InvalidRequestException("minExperience must not be greater than maxExperience");
        }
    }

    /** Whitelists sort fields, applies the default sort and adds `id` as a tie-breaker for stable paging. */
    private static Pageable sanitize(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = DEFAULT_SORT;
        }
        List<Sort.Order> orders = sort.toList();
        for (Sort.Order order : orders) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new InvalidRequestException(
                        "Cannot sort by '" + order.getProperty() + "'. Allowed: " + SORTABLE.stream().sorted().toList());
            }
        }
        if (orders.stream().noneMatch(o -> o.getProperty().equals("id"))) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
