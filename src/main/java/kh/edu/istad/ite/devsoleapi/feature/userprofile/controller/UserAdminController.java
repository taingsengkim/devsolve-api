package kh.edu.istad.ite.devsoleapi.feature.userprofile.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.AdminUserSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final UserProfileService userProfileService;

    @GetMapping
    public Page<AdminUserSummaryResponse> getAllUsers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return userProfileService.getAllForAdmin(
                query,
                status,
                pageNumber,
                pageSize
        );
    }
}
