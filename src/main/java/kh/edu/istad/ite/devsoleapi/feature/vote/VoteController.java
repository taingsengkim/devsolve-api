package kh.edu.istad.ite.devsoleapi.feature.vote;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteRequest;
import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteResponse;
import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/votes")
@RequiredArgsConstructor
public class VoteController {

    private final VoteService voteService;

    @PutMapping("/{type}/{targetId}")
    public VoteResponse setVote(
            @PathVariable VoteType type,
            @PathVariable UUID targetId,
            @Valid @RequestBody VoteRequest request
    ) {
        return voteService.setVote(type, targetId, request);
    }

    @DeleteMapping("/{type}/{targetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeVote(
            @PathVariable VoteType type,
            @PathVariable UUID targetId
    ) {
        voteService.removeVote(type, targetId);
    }

    @GetMapping("/{type}/{targetId}/summary")
    public VoteSummaryResponse getSummary(
            @PathVariable VoteType type,
            @PathVariable UUID targetId
    ) {
        return voteService.getSummary(type, targetId);
    }

    @GetMapping("/mine")
    public Page<VoteResponse> getMine(
            @RequestParam(required = false) VoteType type,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be at least 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize
    ) {
        return voteService.getMine(type, pageNumber, pageSize);
    }
}
