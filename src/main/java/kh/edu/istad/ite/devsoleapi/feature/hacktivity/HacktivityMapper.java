package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.recognition.Recognition;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import org.springframework.stereotype.Component;

@Component
public class HacktivityMapper {

    public HacktivityResponse toResponse(
            Recognition recognition,
            UserProfile user,
            Organization organization,
            Report report,
            Program program
    ) {

        return new HacktivityResponse(
                recognition.getId(),

                new HacktivityResponse.User(
                        user.getId(),
                        user.getFullName(),
                        user.getAvatarUrl()
                ),

                new HacktivityResponse.Organization(
                        organization.getId(),
                        organization.getName()
                ),

                new HacktivityResponse.Report(
                        report.getId(),
                        report.getTitle(),
                        report.getSeverity().name()
                ),

                new HacktivityResponse.Recognition(
                        recognition.getId(),
                        recognition.getTitle(),
                        recognition.getDescription()
                ),

                new HacktivityResponse.Program(
                        program.getId(),
                        program.getName()
                ),

                recognition.getCreatedAt()
        );
    }
}