package kh.edu.istad.ite.devsoleapi.feature.bookmark;

import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkRequest;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookmarkMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Bookmark toEntity(BookmarkRequest request);

    @Mapping(source = "user.id", target = "userId")
    @Mapping(target = "userFullName", ignore = true)
    @Mapping(target = "bookmarkableName", ignore = true)
    BookmarkResponse toResponse(Bookmark bookmark);
}