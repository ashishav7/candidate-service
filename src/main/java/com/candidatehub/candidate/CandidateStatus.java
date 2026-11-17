package com.candidatehub.candidate;

/** Hiring-pipeline stage of a candidate. Persisted as its name (never ordinal). */
public enum CandidateStatus {
    NEW, SCREENING, INTERVIEWING, OFFERED, HIRED, REJECTED
}
