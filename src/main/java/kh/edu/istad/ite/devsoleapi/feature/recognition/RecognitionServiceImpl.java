package kh.edu.istad.ite.devsoleapi.feature.recognition;

import jakarta.persistence.EntityNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.CreateRecognitionRequest;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.RecognitionResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecognitionServiceImpl implements RecognitionService {

    private final RecognitionRepository recognitionRepository;
    private final UserProfileRepository userProfileRepository;
    private final RecognitionMapper recognitionMapper;
    private final ReportRepository reportRepository;

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

        Report report = reportRepository.findById(request.reportId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Report not found: " + request.reportId()
                ));

        Recognition recognition = new Recognition();
        recognition.setUserId(request.userId());
        recognition.setProgramId(request.programId());
        recognition.setReportId(request.reportId());
        recognition.setTitle(request.title());
        recognition.setDescription(request.description());
        recognition.setAwardedBy(awardedBy);
        recognition.setAwardedAt(LocalDateTime.now());

        recognition.setSeverity(report.getSeverity());

        Recognition saved = recognitionRepository.save(recognition);

        recipient.setRecognitionCount(recipient.getRecognitionCount() + 1);
        userProfileRepository.save(recipient);

        return recognitionMapper.toResponse(saved);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<RecognitionResponse> getUserRecognitions(UUID userId, Pageable pageable) {

        if (!userProfileRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }

        return recognitionRepository
                .findByUserIdOrderByAwardedAtDesc(userId, pageable)
                .map(recognitionMapper::toResponse);
    }

    @Override
    public List<RecognitionResponse> getRecognitionsByUser(UUID userId) {

        // Verify the user exists
        userProfileRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        List<Recognition> recognitions =
                recognitionRepository.findAllByUserId(userId);

        return recognitionMapper.toResponse(recognitions);
    }

}