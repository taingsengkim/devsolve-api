package kh.edu.istad.ite.devsoleapi.feature.reputation;

import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import org.springframework.stereotype.Component;

@Component
public class LeaderboardMapper {
    public LeaderboardResponse toResponse(UserProfile user, Integer rank) {

        return new LeaderboardResponse(

                rank,

                user.getId(),

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
}
