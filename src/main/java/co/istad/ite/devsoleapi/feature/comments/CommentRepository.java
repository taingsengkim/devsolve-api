package co.istad.ite.devsoleapi.feature.comments;

import co.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByCommentableTypeAndCommentableId(CommentableType commentableType, UUID commentableId);
}
