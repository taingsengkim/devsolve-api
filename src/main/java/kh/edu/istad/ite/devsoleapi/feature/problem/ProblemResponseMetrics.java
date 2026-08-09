package kh.edu.istad.ite.devsoleapi.feature.problem;

record ProblemResponseMetrics(
        long solutionCount,
        long commentCount,
        long voteScore,
        long bookmarkCount,
        boolean bookmarkedByViewer,
        String viewerVote,
        boolean canEdit,
        boolean canDelete,
        boolean canAcceptSolution
) {
    static ProblemResponseMetrics empty() {
        return new ProblemResponseMetrics(
                0,
                0,
                0,
                0,
                false,
                null,
                false,
                false,
                false
        );
    }
}
