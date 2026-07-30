package kh.edu.istad.ite.devsoleapi.feature.comments.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateCommentRequest {

    @NotBlank(message = "Content is required.")
    private String content;

    private UUID parentCommentId;
}
