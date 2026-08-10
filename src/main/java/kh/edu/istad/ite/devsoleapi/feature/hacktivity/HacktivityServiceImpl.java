package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.recognition.Recognition;
import kh.edu.istad.ite.devsoleapi.feature.recognition.RecognitionRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HacktivityServiceImpl implements HacktivityService {

    private final RecognitionRepository recognitionRepository;
    private final ReportRepository reportRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProgramRepository programRepository;
    private final OrganizationRepository organizationRepository;
    private final HacktivityMapper hacktivityMapper;

    @Override
    public Page<HacktivityResponse> findAll(Pageable pageable) {

        Page<Recognition> recognitions =
                recognitionRepository.findAll(pageable);

        return recognitions
                .map(recognition -> {

                    Report report = reportRepository
                            .findById(recognition.getReportId())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Report not found: " + recognition.getReportId()
                            ));

                    if (report.getState() != ReportState.RESOLVED) {
                        return null;
                    }

                    UserProfile user = userProfileRepository
                            .findById(recognition.getUserId())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "User profile not found: " + recognition.getUserId()
                            ));

                    Program program = programRepository
                            .findById(recognition.getProgramId())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Program not found: " + recognition.getProgramId()
                            ));


                    Organization organization = organizationRepository
                            .findById(program.getOrganizationId())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Organization not found: " + program.getOrganizationId()
                            ));

                    return hacktivityMapper.toResponse(
                            recognition,
                            user,
                            organization,
                            report,
                            program
                    );
                });
    }
}