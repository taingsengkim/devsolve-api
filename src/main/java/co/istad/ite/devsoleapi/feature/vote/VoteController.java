package co.istad.ite.devsoleapi.feature.vote;

import co.istad.ite.devsoleapi.feature.vote.dto.VoteRequest;
import co.istad.ite.devsoleapi.feature.vote.dto.VoteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/votes")
@RequiredArgsConstructor
public class VoteController {
    private final VoteService voteService;
    @PostMapping
    public ResponseEntity<VoteResponse> vote(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid VoteRequest request){

        UUID userId = getUserId(jwt);
        VoteResponse response = voteService.vote(userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteVote(  @AuthenticationPrincipal Jwt jwt,@RequestParam VoteType type, @RequestParam UUID id){
        UUID userId = getUserId(jwt);
        voteService.deleteVote(
                userId, type, id);
        return ResponseEntity.noContent().build();
    }

    private UUID getUserId(Jwt jwt){
        return UUID.fromString(jwt.getSubject());

    }



}