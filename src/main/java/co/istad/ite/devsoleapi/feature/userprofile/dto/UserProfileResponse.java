package co.istad.ite.devsoleapi.feature.userprofile.dto;

import co.istad.ite.devsoleapi.feature.userprofile.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record  UserProfileResponse(
        String id,
        String email,
        String fullName,
        String biography,
        String phone,
        String avatarUrl,
        LocalDate dateOfBirth,
        UserStatus status
) {
}
