package co.istad.ite.devsoleapi.feature.comments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateCommentRequest {

    @NotBlank(message = "Content is required.")
    private String content;

    @NotNull(message = "Author ID is required.")
    private UUID authorId;

    private UUID parentCommentId;
}
