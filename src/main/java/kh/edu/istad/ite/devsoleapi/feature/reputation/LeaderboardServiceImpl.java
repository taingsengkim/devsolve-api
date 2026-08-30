package kh.edu.istad.ite.devsoleapi.feature.reputation;


import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardResponse;
import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardSlice;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LeaderboardServiceImpl implements LeaderboardService {

    private final LeaderboardCache leaderboardCache;

    @Override
    public Page<LeaderboardResponse> getLeaderboard(Pageable pageable) {

        LeaderboardSlice slice = leaderboardCache.load(
                pageable.getPageNumber(),
                pageable.getPageSize()
        );

        return new PageImpl<>(
                slice.content(),
                pageable,
                slice.totalElements()
        );
    }
}
