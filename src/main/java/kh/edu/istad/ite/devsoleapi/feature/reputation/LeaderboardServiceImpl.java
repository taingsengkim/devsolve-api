package kh.edu.istad.ite.devsoleapi.feature.reputation;


import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class LeaderboardServiceImpl implements LeaderboardService {

    private final UserProfileRepository userProfileRepository;
    private final LeaderboardMapper leaderboardMapper;

    /**
     * Rank is positional — the row's place in the ordering, not a competition
     * rank that gives tied researchers the same number. Two people on equal
     * reputation therefore get consecutive ranks, decided by the id tiebreaker
     * in the query. That keeps rank derivable from the page offset alone
     * instead of costing a count query per row, and it stays consistent as the
     * reader pages through.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<LeaderboardResponse> getLeaderboard(Pageable pageable) {

        int startRank =
                pageable.getPageNumber() * pageable.getPageSize() + 1;

        AtomicInteger rank = new AtomicInteger(startRank);

        return userProfileRepository
                .findAllByStatusOrderByReputationDescIdAsc(
                        UserStatus.ACTIVE,
                        pageable
                )
                .map(user -> leaderboardMapper.toResponse(
                        user,
                        rank.getAndIncrement()
                ));
    }
}
