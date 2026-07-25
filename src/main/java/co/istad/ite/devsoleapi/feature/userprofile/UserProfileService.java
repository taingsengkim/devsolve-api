package co.istad.ite.devsoleapi.feature.userprofile;


import co.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import co.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import org.springframework.data.domain.Page;

public interface UserProfileService {
    UserProfileResponse me();

    Page<ShowCasesResponse> getUserShowCases(
            String userId,
            int pageNumber,
            int pageSize
    );


}
