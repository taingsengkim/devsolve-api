package kh.edu.istad.ite.devsoleapi.feature.follow;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowTargetAccessService {

    private final UserProfileRepository userProfileRepository;
    private final OrganizationRepository organizationRepository;
    private final ProblemRepository problemRepository;
    private final ProgramRepository programRepository;
    private final ShowCasesRepository showCasesRepository;

    public void requireFollowable(FollowType type, UUID targetId) {
        switch (type) {
            case USER -> userProfileRepository.findById(targetId)
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .orElseThrow(() -> notFound(type));
            case ORGANIZATION -> organizationRepository
                    .findByIdAndStatusAndDeletedAtIsNull(
                            targetId,
                            OrganizationStatus.ACTIVE
                    )
                    .orElseThrow(() -> notFound(type));
            case PROBLEM -> problemRepository.findPublicById(targetId)
                    .orElseThrow(() -> notFound(type));
            case PROGRAM -> programRepository
                    .findByIdAndStateAndSubmissionStateAndVisibilityAndDeletedAtIsNull(
                            targetId,
                            ProgramState.ACTIVE,
                            SubmissionState.APPROVED,
                            Visibility.PUBLIC
                    )
                    .orElseThrow(() -> notFound(type));
            case SHOWCASE -> showCasesRepository
                    .findByIdAndReviewStatusAndDeletedAtIsNull(
                            targetId,
                            ReviewStatus.APPROVED
                    )
                    .orElseThrow(() -> notFound(type));
        }
    }

    private ResourceNotFoundException notFound(FollowType type) {
        return new ResourceNotFoundException(
                "Public " + type.name().toLowerCase()
                        + " target not found"
        );
    }
}
