package kh.edu.istad.ite.devsoleapi.feature.recognition;

import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.ProgramSummary;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.RecognitionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RecognitionMapper {

    // A Recognition holds the program as a bare id, so this leaves the summary
    // empty by design; the overload below is what fills it.
    @Mapping(target = "program", ignore = true)
    RecognitionResponse toResponse(Recognition recognition);

    List<RecognitionResponse> toResponse(List<Recognition> recognitions);

    /**
     * The same response with the program named.
     *
     * <p>Written out rather than generated from two sources: MapStruct would
     * have to choose between {@code Recognition.id} and
     * {@code ProgramSummary.id} for the target's {@code id}, and the way it
     * resolves that is not something to leave to a naming coincidence.
     */
    default RecognitionResponse toResponse(
            Recognition recognition,
            ProgramSummary program
    ) {

        RecognitionResponse response = toResponse(recognition);

        return new RecognitionResponse(
                response.id(),
                response.userId(),
                response.programId(),
                response.reportId(),
                response.title(),
                response.description(),
                response.awardedBy(),
                response.awardedAt(),
                response.severity(),
                program,
                response.createdAt(),
                response.updatedAt()
        );
    }
}
