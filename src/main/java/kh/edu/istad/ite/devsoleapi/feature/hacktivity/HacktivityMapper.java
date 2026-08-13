package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;
import org.springframework.stereotype.Component;

@Component
public class HacktivityMapper {

    public HacktivityResponse toResponse(Hacktivity hacktivity) {

        var user = hacktivity.getUser();
        var organization = hacktivity.getOrganization();
        var report = hacktivity.getReport();
        var recognition = hacktivity.getRecognition();
        var program = hacktivity.getProgram();

        return new HacktivityResponse(

                hacktivity.getId(),

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

                hacktivity.getCreatedAt()
        );
    }
}