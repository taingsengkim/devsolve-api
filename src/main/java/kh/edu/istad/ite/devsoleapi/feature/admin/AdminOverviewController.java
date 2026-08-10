package kh.edu.istad.ite.devsoleapi.feature.admin;

import kh.edu.istad.ite.devsoleapi.feature.admin.dto.AdminOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminOverviewController {

    private final AdminOverviewService adminOverviewService;

    @GetMapping("/overview")
    public AdminOverviewResponse getOverview() {
        return adminOverviewService.getOverview();
    }
}
