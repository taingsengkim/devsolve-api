package kh.edu.istad.ite.devsoleapi.feature.security;

import jakarta.persistence.criteria.Predicate;
import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.security.dto.SecurityIncidentResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.virustotal.AttachmentScanContext;
import kh.edu.istad.ite.devsoleapi.feature.virustotal.VirusTotalScanResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityIncidentServiceImpl implements SecurityIncidentService {

    /** See {@code HacktivityServiceImpl}: an underscore is a LIKE wildcard. */
    private static final char LIKE_ESCAPE = '\\';

    private final SecurityIncidentRepository securityIncidentRepository;
    private final UserProfileRepository userProfileRepository;
    private final OrganizationRepository organizationRepository;

    /**
     * REQUIRES_NEW because the caller is on its way to a 422: the upload's
     * transaction will be rolled back, and a row written into it would be
     * rolled back with it. This is the one write that has to outlive the
     * refusal, since the refusal is the thing being recorded.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID uploaderUserId,
            AttachmentValidator.ValidatedAttachment attachment,
            String sha256,
            VirusTotalScanResponse result,
            AttachmentScanContext context
    ) {
        Optional<UserProfile> uploader = uploaderUserId == null
                ? Optional.empty()
                : userProfileRepository.findById(uploaderUserId);

        Optional<Organization> organization =
                context.organizationId() == null
                        ? Optional.empty()
                        : organizationRepository.findById(
                                context.organizationId()
                        );

        Map<String, Integer> stats = result.stats();

        securityIncidentRepository.save(
                SecurityIncident.builder()
                        .uploaderUserId(uploaderUserId)
                        .uploaderUsername(
                                uploader.map(UserProfile::getUsername)
                                        .orElse(null)
                        )
                        .uploaderEmail(
                                uploader.map(UserProfile::getEmail)
                                        .orElse(null)
                        )
                        .organizationId(context.organizationId())
                        .organizationName(
                                organization.map(Organization::getName)
                                        .orElse(null)
                        )
                        .reportId(context.notifiableId())
                        .filename(attachment.originalFileName())
                        .fileSizeBytes(attachment.sizeBytes())
                        .sha256Hash(sha256)
                        .verdict(result.verdict())
                        .maliciousEnginesCount(count(stats, "malicious"))
                        .suspiciousEnginesCount(count(stats, "suspicious"))
                        .totalEnginesCount(total(stats))
                        .analysisId(result.analysisId())
                        .blockedAt(LocalDateTime.now())
                        .build()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SecurityIncidentResponse> search(
            UUID organizationId,
            String search,
            VirusTotalScanResponse.Verdict verdict,
            Pageable pageable
    ) {
        return securityIncidentRepository
                .findAll(specificationFor(organizationId, search, verdict),
                        pageable)
                .map(this::toResponse);
    }

    private Specification<SecurityIncident> specificationFor(
            UUID organizationId,
            String search,
            VirusTotalScanResponse.Verdict verdict
    ) {
        return (root, query, builder) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (organizationId != null) {
                predicates.add(builder.equal(
                        root.get("organizationId"), organizationId
                ));
            }

            if (verdict != null) {
                predicates.add(builder.equal(root.get("verdict"), verdict));
            }

            String term = searchTerm(search);

            if (term != null) {
                predicates.add(builder.or(
                        like(builder, root.get("uploaderUsername"), term),
                        like(builder, root.get("uploaderEmail"), term),
                        like(builder, root.get("filename"), term),
                        like(builder, root.get("sha256Hash"), term)
                ));
            }

            return predicates.isEmpty()
                    ? null
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private jakarta.persistence.criteria.Predicate like(
            jakarta.persistence.criteria.CriteriaBuilder builder,
            jakarta.persistence.criteria.Expression<String> field,
            String term
    ) {
        return builder.like(builder.lower(field), term, LIKE_ESCAPE);
    }

    private String searchTerm(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.strip()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private SecurityIncidentResponse toResponse(SecurityIncident incident) {
        return new SecurityIncidentResponse(
                incident.getId(),
                new SecurityIncidentResponse.Uploader(
                        incident.getUploaderUserId(),
                        incident.getUploaderUsername(),
                        incident.getUploaderEmail()
                ),
                incident.getOrganizationId() == null
                        ? null
                        : new SecurityIncidentResponse.Organization(
                                incident.getOrganizationId(),
                                incident.getOrganizationName()
                        ),
                incident.getReportId(),
                incident.getFilename(),
                incident.getFileSizeBytes(),
                incident.getSha256Hash(),
                incident.getVerdict(),
                new SecurityIncidentResponse.Stats(
                        incident.getMaliciousEnginesCount(),
                        incident.getSuspiciousEnginesCount(),
                        incident.getTotalEnginesCount()
                ),
                toInstant(incident.getBlockedAt())
        );
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null
                ? null
                : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private int count(Map<String, Integer> stats, String category) {
        Integer value = stats == null ? null : stats.get(category);
        return value == null ? 0 : value;
    }

    /**
     * Every engine that returned anything. VirusTotal reports one bucket per
     * outcome and omits the empty ones, so the total is their sum rather than
     * a field of its own.
     */
    private int total(Map<String, Integer> stats) {
        if (stats == null) {
            return 0;
        }
        return stats.values().stream()
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }
}
