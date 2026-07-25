package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;

public record UpdateMemberRoleRequest(
        @NotNull OrgRole role
) {}