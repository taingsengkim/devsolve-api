package kh.edu.istad.ite.devsoleapi.feature.recognition;

import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.RecognitionResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RecognitionMapper {
    RecognitionResponse toResponse(Recognition recognition);

    List<RecognitionResponse> toResponse(List<Recognition> recognitions);



}