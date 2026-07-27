package kh.edu.istad.ite.devsoleapi.feature.userprofile;

import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesMapper;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private final Keycloak keycloak;
    private final KeycloakAdminProps props;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper userProfileMapper;
    private final ShowCasesRepository showCasesRepository;
    private final ShowCasesMapper showCasesMapper;

    @Override
    public UserProfileResponse me() {
        String userId = AuthUtils.extractUserId();
        UserRepresentation keycloakUser = keycloak.realm(props.getTargetRealm())
                .users()
                .get(userId)
                .toRepresentation();

        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile has not been found"));

        return userProfileMapper.toUserProfileResponse(keycloakUser, userProfile);    }

    @Override
    public Page<ShowCasesResponse> getUserShowCases(String userId, int pageNumber, int pageSize) {
        userProfileRepository.findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User profile has not been found"
                        )
                );

        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(
                        Sort.Direction.DESC,
                        "createdAt"
                )
        );

        return showCasesRepository
                .findByAuthor_IdAndReviewStatusAndDeletedAtIsNull(
                        userId,
                        ReviewStatus.APPROVED,
                        pageable
                )
                .map(showCasesMapper::mapShowCaseToShowCaseResponse);
    }

}

