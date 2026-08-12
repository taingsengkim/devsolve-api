package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HacktivityServiceImpl implements HacktivityService {

    private final HacktivityRepository hacktivityRepository;
    private final HacktivityMapper hacktivityMapper;

    @Override
    public Page<HacktivityResponse> findAll(Pageable pageable) {

        return hacktivityRepository
                .findAll(pageable)
                .map(hacktivityMapper::toResponse);
    }
}