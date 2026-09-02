package kh.edu.istad.ite.devsoleapi.feature.recognition;

import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.CreateRecognitionRequest;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.RecognitionResponse;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.ThanksResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RecognitionService {

    RecognitionResponse awardRecognition(
            CreateRecognitionRequest request,
            UUID awardedBy
    );

    Page<RecognitionResponse> getRecognitionsByUser(
            UUID userId,
            Pageable pageable
    );

    /** One program's hall of thanks, most thanked first. */
    Page<ThanksResponse> getProgramThanks(
            UUID programId,
            Pageable pageable
    );

    /** The same across every program an organization runs. */
    Page<ThanksResponse> getOrganizationThanks(
            UUID organizationId,
            Pageable pageable
    );
}
