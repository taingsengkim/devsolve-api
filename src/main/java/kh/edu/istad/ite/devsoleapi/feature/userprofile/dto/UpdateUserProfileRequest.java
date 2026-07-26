package kh.edu.istad.ite.devsoleapi.feature.userprofile.dto;

public record UpdateUserProfileRequest (
        String email,
        String fullName,
        String biography,
        String phone,
        String avatarUrl
){
}
