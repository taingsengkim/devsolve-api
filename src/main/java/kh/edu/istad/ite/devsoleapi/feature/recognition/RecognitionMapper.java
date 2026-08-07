package kh.edu.istad.ite.devsoleapi.feature.recognition;

import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.RecognitionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RecognitionMapper {
    RecognitionResponse toResponse(Recognition recognition);


}