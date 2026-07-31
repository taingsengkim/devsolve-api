package kh.edu.istad.ite.devsoleapi.feature.bookmark;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bookmarks")
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserProfile user;

    @Column(name = "bookmarkable_type", nullable = false, length = 20)
    private String bookmarkableType;  // "POST", "COMMENT", "TOPIC", etc.

    @Column(name = "bookmarkable_id", nullable = false)
    private String bookmarkableId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}