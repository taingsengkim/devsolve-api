package kh.edu.istad.ite.devsoleapi.feature.program.program_reward;


import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "program_rewards")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramReward {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    @JsonIgnore
    private Program program;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Integer points;
    
}