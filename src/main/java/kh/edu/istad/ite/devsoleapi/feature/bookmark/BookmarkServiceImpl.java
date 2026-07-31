package kh.edu.istad.ite.devsoleapi.feature.bookmark;

import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkRequest;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkServiceImpl implements BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final BookmarkMapper bookmarkMapper;
    private final UserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public BookmarkResponse bookmark(BookmarkRequest request) {
        log.info("Processing bookmark request: {}", request);


        UserProfile user = userProfileRepository.findById(UUID.fromString(request.userId()))
                .orElseThrow(() -> new RuntimeException("User not found with id: " + request.userId()));


        if (bookmarkRepository.existsByUserIdAndBookmarkableTypeAndBookmarkableId(
                request.userId(),
                request.bookmarkableType(),
                request.bookmarkableId())) {
            throw new RuntimeException("Already bookmarked this " + request.bookmarkableType());
        }


        validateBookmarkableExists(request.bookmarkableType(), request.bookmarkableId());


        Bookmark bookmark = bookmarkMapper.toEntity(request);
        bookmark.setUser(user);

        Bookmark saved = bookmarkRepository.save(bookmark);
        log.info("Successfully created bookmark with id: {}", saved.getId());


        BookmarkResponse response = bookmarkMapper.toResponse(saved);
        return enrichBookmarkResponse(response);
    }

    @Override
    @Transactional
    public void unbookmark(String userId, String bookmarkableType, String bookmarkableId) {
        log.info("Processing unbookmark request - user: {}, type: {}, id: {}",
                userId, bookmarkableType, bookmarkableId);

        Bookmark bookmark = bookmarkRepository
                .findByUserIdAndBookmarkableTypeAndBookmarkableId(userId, bookmarkableType, bookmarkableId)
                .orElseThrow(() -> new RuntimeException("Bookmark not found"));

        bookmarkRepository.delete(bookmark);
        log.info("Successfully removed bookmark");
    }

    @Override
    public List<BookmarkResponse> getUserBookmarks(String userId) {
        log.info("Getting bookmarks for user: {}", userId);

        if (!userProfileRepository.existsById(UUID.fromString(userId))) {
            throw new RuntimeException("User not found with id: " + userId);
        }

        List<Bookmark> bookmarks = bookmarkRepository.findByUserId(userId);
        return bookmarks.stream()
                .map(bookmarkMapper::toResponse)
                .map(this::enrichBookmarkResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<BookmarkResponse> getBookmarkers(String bookmarkableType, String bookmarkableId) {
        log.info("Getting bookmarkers for - type: {}, id: {}", bookmarkableType, bookmarkableId);

        List<Bookmark> bookmarks = bookmarkRepository.findByBookmarkableTypeAndBookmarkableId(
                bookmarkableType, bookmarkableId);

        return bookmarks.stream()
                .map(bookmarkMapper::toResponse)
                .map(this::enrichBookmarkResponse)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isBookmarked(String userId, String bookmarkableType, String bookmarkableId) {
        return bookmarkRepository.existsByUserIdAndBookmarkableTypeAndBookmarkableId(
                userId, bookmarkableType, bookmarkableId);
    }

    @Override
    public long countUserBookmarks(String userId) {
        return bookmarkRepository.countByUserId(userId);
    }

    @Override
    public long countBookmarks(String bookmarkableType, String bookmarkableId) {
        return bookmarkRepository.countByBookmarkableTypeAndBookmarkableId(
                bookmarkableType, bookmarkableId);
    }

    private void validateBookmarkableExists(String bookmarkableType, String bookmarkableId) {
        if (bookmarkableType.equalsIgnoreCase("USER")) {
            userProfileRepository.findById(UUID.fromString(bookmarkableId))
                    .orElseThrow(() -> new RuntimeException("User to bookmark not found with id: " + bookmarkableId));
        }
    }

    private BookmarkResponse enrichBookmarkResponse(BookmarkResponse response) {
        String userFullName = null;
        String bookmarkableName = null;


        UserProfile user = userProfileRepository.findById(UUID.fromString(response.userId())).orElse(null);
        if (user != null) {
            userFullName = user.getFullName();
        }

        if (response.bookmarkableType().equalsIgnoreCase("USER")) {
            UserProfile bookmarkable = userProfileRepository.findById(UUID.fromString(response.bookmarkableId())).orElse(null);
            if (bookmarkable != null) {
                bookmarkableName = bookmarkable.getFullName();
            }
        }

        return new BookmarkResponse(
                response.id(),
                response.userId(),
                userFullName,
                response.bookmarkableType(),
                response.bookmarkableId(),
                bookmarkableName,
                response.createdAt()
        );
    }
}