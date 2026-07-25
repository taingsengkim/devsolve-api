package kh.edu.istad.ite.devsoleapi.feature.program.program_reward.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import java.math.BigDecimal;

import lombok.Builder;

@Builder
public record ProgramRewardRequestDto(
        Severity severity,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Integer points
) {}