package kh.edu.istad.ite.devsoleapi.feature.bookmark;

import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkResponse;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkStatusResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface BookmarkService {

    BookmarkResponse bookmark(BookmarkType type, UUID targetId);

    void unbookmark(BookmarkType type, UUID targetId);

    BookmarkStatusResponse getStatus(
            BookmarkType type,
            UUID targetId
    );

    Page<BookmarkResponse> getMine(
            BookmarkType type,
            int pageNumber,
            int pageSize
    );
}
