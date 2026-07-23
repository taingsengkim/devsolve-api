package co.istad.ite.devsoleapi.feature.comment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByCommentableTypeAndCommentableIdAndDeletedAtIsNull(
            CommentableType commentableType,
            UUID commentableId
    );

    Optional<Comment> findByIdAndDeletedAtIsNull(UUID id);
}