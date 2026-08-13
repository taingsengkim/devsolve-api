package kh.edu.istad.ite.devsoleapi.feature.reputation;

import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LeaderboardService {

    Page<LeaderboardResponse> getLeaderboard(Pageable pageable);

}