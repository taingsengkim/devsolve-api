package kh.edu.istad.ite.devsoleapi.feature.program.program_reward.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

import lombok.Builder;

@Builder
public record ProgramRewardRequestDto(
        @NotNull(message = "Reward severity is required")
        Severity severity,

        @DecimalMin(value = "0.00", message = "Minimum reward cannot be negative")
        @Digits(
                integer = 8,
                fraction = 2,
                message = "Minimum reward must fit NUMERIC(10,2)"
        )
        BigDecimal minAmount,

        @DecimalMin(value = "0.00", message = "Maximum reward cannot be negative")
        @Digits(
                integer = 8,
                fraction = 2,
                message = "Maximum reward must fit NUMERIC(10,2)"
        )
        BigDecimal maxAmount,

        @PositiveOrZero(message = "Reward points cannot be negative")
        Integer points
) {}
