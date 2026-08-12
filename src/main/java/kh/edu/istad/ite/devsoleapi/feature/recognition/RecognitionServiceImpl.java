package kh.edu.istad.ite.devsoleapi.feature.recognition;


import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.Hacktivity;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.HacktivityRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.CreateRecognitionRequest;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.RecognitionResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecognitionServiceImpl implements RecognitionService {

    private final RecognitionRepository recognitionRepository;

    private final RecognitionMapper recognitionMapper;

    private final HacktivityRepository hacktivityRepository;

    private final UserProfileRepository userProfileRepository;

    private final ReportRepository reportRepository;

    private final ProgramRepository programRepository;

    private final OrganizationRepository organizationRepository;


    @Override
    @Transactional
    public RecognitionResponse awardRecognition(
            CreateRecognitionRequest request,
            UUID awardedBy
    ) {

        UserProfile user = userProfileRepository
                .findById(request.userId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User profile not found: " + request.userId()
                        )
                );

        Report report = reportRepository
                .findById(request.reportId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Report not found: " + request.reportId()
                        )
                );

        if (!report.getState().equals(ReportState.RESOLVED)) {
            throw new IllegalStateException(
                    "Recognition can only be awarded for a resolved report"
            );
        }


        Program program = programRepository
                .findById(request.programId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Program not found: " + request.programId()
                        )
                );


        Organization organization = organizationRepository
                .findById(program.getOrganizationId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Organization not found: "
                                        + program.getOrganizationId()
                        )
                );

        Recognition recognition = new Recognition();

        recognition.setUserId(request.userId());
        recognition.setProgramId(request.programId());
        recognition.setReportId(request.reportId());
        recognition.setTitle(request.title());
        recognition.setDescription(request.description());
        recognition.setAwardedBy(awardedBy);
        recognition.setAwardedAt(LocalDateTime.now());
        recognition.setSeverity(report.getSeverity());


        recognition = recognitionRepository.save(recognition);


        Hacktivity hacktivity = Hacktivity.builder()
                .recognition(recognition)
                .user(user)
                .organization(organization)
                .report(report)
                .program(program)
                .createdAt(LocalDateTime.now())
                .build();


        hacktivityRepository.save(hacktivity);

        return recognitionMapper.toResponse(recognition);
    }



    @Override
    @Transactional(readOnly = true)
    public List<RecognitionResponse> getRecognitionsByUser(
            UUID userId
    ) {

        userProfileRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + userId
                        )
                );

        List<Recognition> recognitions =
                recognitionRepository.findAllByUserId(userId);

        return recognitionMapper.toResponse(recognitions);
    }
}