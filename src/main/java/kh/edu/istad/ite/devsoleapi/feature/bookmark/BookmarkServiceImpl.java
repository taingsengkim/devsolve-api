package kh.edu.istad.ite.devsoleapi.feature.bookmark;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkResponse;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkStatusResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookmarkServiceImpl implements BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final UserProfileRepository userProfileRepository;
    private final BookmarkTargetAccessService targetAccessService;

    @Override
    @Transactional
    public BookmarkResponse bookmark(
            BookmarkType type,
            UUID targetId
    ) {
        UUID userId = currentUserId();
        BookmarkTarget target = targetAccessService.requireBookmarkable(
                type,
                targetId
        );
        userProfileRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "An active user profile is required"
                ));

        bookmarkRepository.insertIfAbsent(
                UUID.randomUUID(),
                userId,
                type.databaseValue(),
                targetId
        );
        Bookmark bookmark = bookmarkRepository
                .findByUser_IdAndBookmarkableTypeAndBookmarkableId(
                        userId,
                        type,
                        targetId
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Bookmark upsert completed without a stored bookmark"
                ));
        return toResponse(bookmark, target);
    }

    @Override
    @Transactional
    public void unbookmark(BookmarkType type, UUID targetId) {
        bookmarkRepository
                .deleteByUser_IdAndBookmarkableTypeAndBookmarkableId(
                        currentUserId(),
                        type,
                        targetId
                );
    }

    @Override
    @Transactional(readOnly = true)
    public BookmarkStatusResponse getStatus(
            BookmarkType type,
            UUID targetId
    ) {
        targetAccessService.requireBookmarkable(type, targetId);
        return new BookmarkStatusResponse(
                type,
                targetId,
                bookmarkRepository
                        .existsByUser_IdAndBookmarkableTypeAndBookmarkableId(
                                currentUserId(),
                                type,
                                targetId
                        )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookmarkResponse> getMine(
            BookmarkType type,
            int pageNumber,
            int pageSize
    ) {
        return bookmarkRepository.findMine(
                currentUserId(),
                type,
                PageRequest.of(
                        pageNumber,
                        pageSize,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
        ).map(this::toResponse);
    }

    private BookmarkResponse toResponse(Bookmark bookmark) {
        return targetAccessService.findBookmarkable(
                        bookmark.getBookmarkableType(),
                        bookmark.getBookmarkableId()
                )
                .map(target -> toResponse(bookmark, target))
                .orElseGet(() -> new BookmarkResponse(
                        bookmark.getId(),
                        bookmark.getBookmarkableType(),
                        bookmark.getBookmarkableId(),
                        false,
                        null,
                        null,
                        null,
                        bookmark.getCreatedAt()
                ));
    }

    private BookmarkResponse toResponse(
            Bookmark bookmark,
            BookmarkTarget target
    ) {
        return new BookmarkResponse(
                bookmark.getId(),
                bookmark.getBookmarkableType(),
                bookmark.getBookmarkableId(),
                true,
                target.title(),
                target.preview(),
                target.imageUrl(),
                bookmark.getCreatedAt()
        );
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }
}
