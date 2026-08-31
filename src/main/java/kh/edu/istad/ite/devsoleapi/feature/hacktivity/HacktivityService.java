package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityFilter;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityStatsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HacktivityService {

    /**
     * One page of the feed, narrowed by {@code filter}. A filter that matches
     * nothing is an empty page, not an error.
     */
    Page<HacktivityResponse> search(HacktivityFilter filter, Pageable pageable);

    HacktivityStatsResponse getStats();
}
