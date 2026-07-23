package co.istad.ite.devsoleapi.feature.userprofile.dto;

import co.istad.ite.devsoleapi.feature.userprofile.UserStatus;

import java.time.LocalDate;

public record UpdateUserProfileRequest (
        String email,
        String fullName,
        String biography,
        String phone,
        String avatarUrl
){
}
