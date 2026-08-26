package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Weakness;
import org.springframework.stereotype.Component;

@Component
public class WeaknessMapper {

    public WeaknessResponse toResponse(Weakness weakness) {
        return new WeaknessResponse(
                weakness.getId(),
                weakness.getCweId(),
                weakness.getName(),
                weakness.getDescription(),
                weakness.getIsActive(),
                weakness.getCreatedAt()
        );
    }
}
