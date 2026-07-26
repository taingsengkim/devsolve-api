package kh.edu.istad.ite.devsoleapi.feature.program.program_reward.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;

import java.math.BigDecimal;
import java.util.UUID;

public record ProgramRewardResponseDto(
        UUID id,
        Severity severity,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Integer points
) {
}
