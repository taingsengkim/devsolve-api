package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HacktivityService {

    Page<HacktivityResponse> findAll(Pageable pageable);
}