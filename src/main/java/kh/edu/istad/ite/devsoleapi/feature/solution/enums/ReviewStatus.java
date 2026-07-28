package kh.edu.istad.ite.devsoleapi.feature.solution.enums;

public enum ReviewStatus {
    PENDING,    // waiting for admin moderation
    APPROVED,   // approved by admin – publicly visible
    REJECTED,   // rejected with a reason
    ACCEPTED    // accepted by problem author (overrides APPROVED, still visible)
}