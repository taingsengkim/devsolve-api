package kh.edu.istad.ite.devsoleapi.feature.recognition;

import jakarta.persistence.EntityNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.CreateRecognitionRequest;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.RecognitionResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecognitionServiceImpl implements RecognitionService {

    private final RecognitionRepository recognitionRepository;
    private final UserProfileRepository userProfileRepository;
    private final RecognitionMapper recognitionMapper;

    @Override
    @Transactional
    public RecognitionResponse awardRecognition(CreateRecognitionRequest request, UUID awardedBy) {

        UserProfile recipient = userProfileRepository.findById(request.userId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found: " + request.userId()));

        if (recipient.getId().equals(awardedBy)) {
            throw new IllegalArgumentException("You cannot award recognition to yourself");
        }

        // TODO: validate programId exists once ProgramRepository is available
        // TODO: validate reportId exists (if provided) once ReportRepository is available

        Recognition recognition = new Recognition();
        recognition.setUserId(request.userId());
        recognition.setProgramId(request.programId());
        recognition.setReportId(request.reportId());
        recognition.setTitle(request.title());
        recognition.setDescription(request.description());
        recognition.setAwardedBy(awardedBy);
        recognition.setAwardedAt(LocalDateTime.now());

        Recognition saved = recognitionRepository.save(recognition);

        recipient.setRecognitionCount(recipient.getRecognitionCount() + 1);
        userProfileRepository.save(recipient);

        return recognitionMapper.toResponse(saved);
    }
}