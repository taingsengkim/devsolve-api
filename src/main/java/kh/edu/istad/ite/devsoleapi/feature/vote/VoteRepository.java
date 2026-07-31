package kh.edu.istad.ite.devsoleapi.feature.vote;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

    Optional<Vote> findByUserIdAndVotableTypeAndVotableId(UUID userId, VoteType votableType, UUID votableId);

    void deleteByUserIdAndVotableTypeAndVotableId(UUID userId, VoteType votableType, UUID votableId);

    boolean existsByUserIdAndVotableTypeAndVotableId(UUID userId, VoteType votableType, UUID votableId);

}