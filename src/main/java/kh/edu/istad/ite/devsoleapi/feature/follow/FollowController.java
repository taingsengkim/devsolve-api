package kh.edu.istad.ite.devsoleapi.feature.follow;

import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowRequest;
import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/follows")
@RequiredArgsConstructor
public class FollowController {
    private final FollowService followService;

    @PostMapping
    public ResponseEntity<FollowResponse> follow(@Valid @RequestBody FollowRequest request) {
        FollowResponse response = followService.follow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/followers")
    public ResponseEntity<List<FollowResponse>> getFollowers(
            @RequestParam String followableType,
            @RequestParam String followableId) {
        List<FollowResponse> followers = followService.getFollowers(followableId, followableType);
        return ResponseEntity.ok(followers);
    }

    @GetMapping("/following/{followerId}")
    public ResponseEntity<List<FollowResponse>> getFollowing(@PathVariable String followerId) {
        List<FollowResponse> follows = followService.getFollowing(followerId);
        return ResponseEntity.ok(follows);
    }

    @GetMapping("/check")
    public ResponseEntity<Boolean> isFollowing(
            @RequestParam String followerId,
            @RequestParam String followableType,
            @RequestParam String followableId) {
        boolean isFollowing = followService.isFollowing(followerId, followableType, followableId);
        return ResponseEntity.ok(isFollowing);
    }

    @GetMapping("/count/following/{followerId}")
    public ResponseEntity<Long> countFollowing(@PathVariable String followerId) {
        long count = followService.countFollowing(followerId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/count/followers")
    public ResponseEntity<Long> countFollowers(
            @RequestParam String followableType,
            @RequestParam String followableId) {
        long count = followService.countFollowers(followableType, followableId);
        return ResponseEntity.ok(count);
    }

    @DeleteMapping("/unfollow")
    public ResponseEntity<Void> unfollow(
            @RequestParam String followerId,
            @RequestParam String followableType,
            @RequestParam String followableId) {
        followService.unfollow(followerId, followableType, followableId);
        return ResponseEntity.noContent().build();  // ✅ Added proper response
    }
}