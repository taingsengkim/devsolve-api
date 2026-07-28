package kh.edu.istad.ite.devsoleapi.feature.solution.dto;

public record SolutionUpdateRequest(
        String description,
        String videoUrl,
        String diagramUrl
) {}