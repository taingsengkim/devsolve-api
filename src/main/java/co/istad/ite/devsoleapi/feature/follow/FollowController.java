package co.istad.ite.devsoleapi.feature.follow;

import co.istad.ite.devsoleapi.feature.follow.dto.FollowRequest;
import co.istad.ite.devsoleapi.feature.follow.dto.FollowResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {
    private final FollowService followService;

    @PostMapping
    public ResponseEntity<FollowResponse> follow(@Valid @RequestBody FollowRequest request) {
        FollowResponse response = followService.follow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping
    public ResponseEntity<Void> unfollow(
            @RequestParam UUID followerId,
            @RequestParam String followableType,
            @RequestParam UUID followableId) {
        followService.unfollow(followerId, followableType, followableId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/following/{followerId}")
    public ResponseEntity<List<FollowResponse>> getFollowing(@PathVariable UUID followerId) {
        List<FollowResponse> follows = followService.getFollowing(followerId);
        return ResponseEntity.ok(follows);
    }

    @GetMapping("/followers")
    public ResponseEntity<List<FollowResponse>> getFollowers(
            @RequestParam String followableType,
            @RequestParam UUID followableId) {
        List<FollowResponse> followers = followService.getFollowers(followableId, followableType);
        return ResponseEntity.ok(followers);
    }

    @GetMapping("/check")
    public ResponseEntity<Boolean> isFollowing(
            @RequestParam UUID followerId,
            @RequestParam String followableType,
            @RequestParam UUID followableId) {
        boolean isFollowing = followService.isFollowing(followerId, followableType, followableId);
        return ResponseEntity.ok(isFollowing);
    }

    @GetMapping("/count/followers")
    public ResponseEntity<Long> countFollowers(
            @RequestParam String followableType,
            @RequestParam UUID followableId) {
        long count = followService.countFollowers(followableType, followableId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/count/following/{followerId}")
    public ResponseEntity<Long> countFollowing(@PathVariable UUID followerId) {
        long count = followService.countFollowing(followerId);
        return ResponseEntity.ok(count);
    }
}
