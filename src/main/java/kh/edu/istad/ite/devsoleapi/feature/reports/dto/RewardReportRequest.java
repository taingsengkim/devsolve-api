package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RewardReportRequest(
        @DecimalMin(
                value = "0.01",
                message = "Amount must be greater than zero"
        )
        @Digits(
                integer = 8,
                fraction = 2,
                message = "Amount must fit NUMERIC(10,2)"
        )
        BigDecimal amount,

        @PositiveOrZero(message = "Points cannot be negative")
        Integer points,

        @Size(max = 2000, message = "Note must not exceed 2000 characters")
        String note
) {
}
