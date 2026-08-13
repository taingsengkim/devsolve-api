package kh.edu.istad.ite.devsoleapi.feature.comments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;

import java.util.List;
import java.util.UUID;

/**
 * @param mentionedUserIds who the author is addressing, sent as ids rather
 *                         than parsed back out of the text. A profile here has
 *                         a name and an email and nothing unique to match on,
 *                         so scraping "@someone" out of the content would
 *                         either miss real people or let anyone summon a
 *                         stranger by typing their name. The client already
 *                         knows who it put in the mention picker.
 */
public record CreateCommentRequest(
        @NotNull(message = "Commentable type is required")
        CommentableType commentableType,

        @NotNull(message = "Commentable ID is required")
        UUID commentableId,

        @NotBlank(message = "Content is required")
        @Size(max = 5000, message = "Content must not exceed 5000 characters")
        String content,

        UUID parentCommentId,

        boolean internal,

        @Size(max = 10, message = "A comment may mention at most 10 people")
        List<UUID> mentionedUserIds
) {

    public CreateCommentRequest {
        mentionedUserIds = mentionedUserIds == null
                ? List.of()
                : List.copyOf(mentionedUserIds);
    }
}
