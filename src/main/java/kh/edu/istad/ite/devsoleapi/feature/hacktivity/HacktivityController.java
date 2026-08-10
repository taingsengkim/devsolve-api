package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/hacktivity")
public class HacktivityController {

    private final HacktivityService hacktivityService;

    @GetMapping
    public Page<HacktivityResponse> getHacktivity(
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return hacktivityService.findAll(pageable);
    }
}