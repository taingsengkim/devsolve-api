package kh.edu.istad.ite.devsoleapi.feature.reputation;


import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class LeaderboardServiceImpl implements LeaderboardService {

    private final UserProfileRepository userProfileRepository;
    private final LeaderboardMapper leaderboardMapper;

    @Override
    public Page<LeaderboardResponse> getLeaderboard(Pageable pageable) {

        Page<UserProfile> page =
                userProfileRepository.findAllByOrderByReputationDesc(pageable);

        int startRank =
                pageable.getPageNumber() * pageable.getPageSize() + 1;

        AtomicInteger rank = new AtomicInteger(startRank);

        List<LeaderboardResponse> responses =
                page.getContent()
                        .stream()
                        .map(user -> leaderboardMapper.toResponse(
                                user,
                                rank.getAndIncrement()
                        ))
                        .toList();

        return new PageImpl<>(
                responses,
                pageable,
                page.getTotalElements()
        );
    }
}
