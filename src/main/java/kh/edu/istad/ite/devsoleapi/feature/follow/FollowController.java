package kh.edu.istad.ite.devsoleapi.feature.follow;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowResponse;
import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowerResponse;
import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowingUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PutMapping("/{type}/{targetId}")
    public FollowResponse follow(
            @PathVariable FollowType type,
            @PathVariable UUID targetId
    ) {
        return followService.follow(type, targetId);
    }

    @DeleteMapping("/{type}/{targetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(
            @PathVariable FollowType type,
            @PathVariable UUID targetId
    ) {
        followService.unfollow(type, targetId);
    }

    @GetMapping("/{type}/{targetId}/summary")
    public FollowSummaryResponse getSummary(
            @PathVariable FollowType type,
            @PathVariable UUID targetId
    ) {
        return followService.getSummary(type, targetId);
    }

    @GetMapping("/mine")
    public Page<FollowResponse> getMine(
            @RequestParam(required = false) FollowType type,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be at least 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize
    ) {
        return followService.getMine(type, pageNumber, pageSize);
    }

    @GetMapping("/users/{userId}/following")
    public Page<FollowResponse> getFollowing(
            @PathVariable UUID userId,
            @RequestParam(required = false) FollowType type,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be at least 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize
    ) {
        return followService.getFollowing(
                userId,
                type,
                pageNumber,
                pageSize
        );
    }

    @GetMapping("/{type}/{targetId}/followers")
    public Page<FollowerResponse> getFollowers(
            @PathVariable FollowType type,
            @PathVariable UUID targetId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be at least 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize
    ) {
        return followService.getFollowers(
                type,
                targetId,
                pageNumber,
                pageSize
        );
    }

    @GetMapping("/users/{userId}/following/users")
    public Page<FollowingUserResponse> getFollowingUsers(
            @PathVariable UUID userId,

            @RequestParam(defaultValue = "0")
            @Min(
                    value = 0,
                    message = "Page number must be at least 0"
            )
            int pageNumber,

            @RequestParam(defaultValue = "20")
            @Min(
                    value = 1,
                    message = "Page size must be at least 1"
            )
            @Max(
                    value = 100,
                    message = "Page size must not exceed 100"
            )
            int pageSize
    ) {
        return followService.getFollowingUsers(
                userId,
                pageNumber,
                pageSize
        );
    }
}
