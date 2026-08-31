package kh.edu.istad.ite.devsoleapi.feature.reputation;

import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import org.springframework.stereotype.Component;

@Component
public class LeaderboardMapper {

    /** The all-time board: every figure is the profile's running total. */
    public LeaderboardResponse toResponse(UserProfile user, Integer rank) {

        return new LeaderboardResponse(

                rank,

                user.getId(),

                user.getUsername(),

                user.getFullName(),

                user.getAvatarUrl(),

                user.getCountry(),

                user.getReputation(),

                user.getTotalReports(),

                user.getValidReports(),

                user.getCriticalReports(),

                user.getRecognitionCount()
        );
    }

    /**
     * A windowed board: the three figures the window can answer come from the
     * window, and the two it cannot are left null rather than quietly filled
     * in from the profile's lifetime totals.
     */
    public LeaderboardResponse toWindowedResponse(
            UserProfile user,
            Integer rank,
            WindowedStanding standing
    ) {

        return new LeaderboardResponse(

                rank,

                user.getId(),

                user.getUsername(),

                user.getFullName(),

                user.getAvatarUrl(),

                user.getCountry(),

                standing.points(),

                null,

                null,

                standing.criticals(),

                standing.recognitions()
        );
    }
}
