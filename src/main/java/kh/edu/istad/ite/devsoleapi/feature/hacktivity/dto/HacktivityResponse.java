package kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record HacktivityResponse(
        UUID id,
        User user,
        Organization organization,
        Report report,
        Recognition recognition,
        Program program,
        LocalDateTime createdAt
) {

    public record User(
            UUID id,
            String username,
            String avatarUrl
    ) {}

    public record Organization(
            UUID id,
            String name
    ) {}

    public record Report(
            UUID id,
            String title,
            String severity
    ) {}

    public record Recognition(
            UUID id,
            String title,
            String description
    ) {}

    public record Program(
            UUID id,
            String name
    ) {}
}
