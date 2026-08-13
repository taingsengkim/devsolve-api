package kh.edu.istad.ite.devsoleapi.feature.recognition;

import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.CreateRecognitionRequest;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.RecognitionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


import java.util.List;
import java.util.UUID;

public interface RecognitionService {
    RecognitionResponse awardRecognition(CreateRecognitionRequest request, UUID awardedBy);

    List<RecognitionResponse> getRecognitionsByUser(UUID userId);
}