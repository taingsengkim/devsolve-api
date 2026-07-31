package kh.edu.istad.ite.devsoleapi.feature.recognition;

import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.CreateRecognitionRequest;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.RecognitionResponse;

import java.util.UUID;

public interface RecognitionService {
    RecognitionResponse awardRecognition(CreateRecognitionRequest request, UUID awardedBy);
}