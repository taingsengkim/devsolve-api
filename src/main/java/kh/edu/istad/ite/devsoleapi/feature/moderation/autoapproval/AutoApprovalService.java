package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval.dto.AutoApprovalSettingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The switches, and who may move them.
 *
 * <p>Off unless a row says otherwise, so a database that has never heard of
 * this feature behaves exactly as it did before it existed — and so does one
 * where the row was deleted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoApprovalService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final AutoApprovalSettingRepository settingRepository;
    private final ContentApprovalReviewer reviewer;

    /**
     * Read on its own transaction so the automation can consult it from a
     * background thread that has none, and so a read cannot join and hold open
     * the transaction that is publishing a post.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean isEnabled(AutoApprovalTarget target) {
        return settingRepository.findById(target)
                .map(AutoApprovalSetting::isEnabled)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<AutoApprovalSettingResponse> findAll() {
        requireAdmin();
        return Arrays.stream(AutoApprovalTarget.values())
                .map(target -> settingRepository.findById(target)
                        .map(this::toResponse)
                        .orElseGet(() -> new AutoApprovalSettingResponse(
                                target,
                                false,
                                reviewer.isEnabled(),
                                null,
                                null
                        )))
                .toList();
    }

    @Transactional
    public AutoApprovalSettingResponse setEnabled(
            AutoApprovalTarget target,
            boolean enabled
    ) {
        requireAdmin();
        UUID administrator = currentUserId();

        AutoApprovalSetting setting = settingRepository.findById(target)
                .orElseGet(() -> {
                    AutoApprovalSetting created = new AutoApprovalSetting();
                    created.setTarget(target);
                    return created;
                });
        setting.setEnabled(enabled);
        setting.setUpdatedBy(administrator);
        setting.setUpdatedAt(LocalDateTime.now());

        // Worth a line in the log on its own: this is the one setting that
        // decides whether anything reaches the public feed unread.
        log.warn(
                "Auto-approval for {} switched {} by {}",
                target,
                enabled ? "ON" : "OFF",
                administrator
        );
        return toResponse(settingRepository.saveAndFlush(setting));
    }

    private AutoApprovalSettingResponse toResponse(
            AutoApprovalSetting setting
    ) {
        return new AutoApprovalSettingResponse(
                setting.getTarget(),
                setting.isEnabled(),
                reviewer.isEnabled(),
                setting.getUpdatedBy(),
                setting.getUpdatedAt()
        );
    }

    private void requireAdmin() {
        if (!AuthUtils.hasRole(ADMIN_ROLE)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Required realm role: ADMIN"
            );
        }
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }
}
