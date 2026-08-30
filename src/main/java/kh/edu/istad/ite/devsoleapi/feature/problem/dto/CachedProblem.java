package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import java.util.UUID;

/**
 * A problem response as it goes into the cache: everything that reads the same
 * for everyone, and nothing that belongs to one viewer.
 *
 * @param response  built with empty metrics, so its vote, bookmark and
 *                  permission fields are blank until a request fills them in
 * @param authorId  kept out of band because {@code response.author()} is null
 *                  when the profile has gone missing, and ownership decides
 *                  who may edit
 */
public record CachedProblem(
        ProblemResponse response,
        UUID authorId
) {
}
