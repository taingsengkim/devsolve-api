package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoApprovalSettingRepository
        extends JpaRepository<AutoApprovalSetting, AutoApprovalTarget> {
}
