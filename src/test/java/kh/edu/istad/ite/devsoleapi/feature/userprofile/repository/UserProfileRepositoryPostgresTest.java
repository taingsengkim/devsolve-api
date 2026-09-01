package kh.edu.istad.ite.devsoleapi.feature.userprofile.repository;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code status} is a Postgres named enum, and the admin search binds it as an
 * untyped parameter. Whether the server can work out what that parameter is
 * depends on where it sits in the statement, so the query has to be run against
 * a real Postgres to know it parses at all — the failure was a parse error the
 * database raised, which no mock can reproduce.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create"
})
class UserProfileRepositoryPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Test
    void adminSearchRunsForOneStatusAndForEveryStatus() {
        assertEquals(0, assertDoesNotThrow(() ->
                userProfileRepository.findForAdmin(
                        "%kim%",
                        EnumSet.allOf(UserStatus.class),
                        adminPage()
                )
        ).getTotalElements());

        assertEquals(0, assertDoesNotThrow(() ->
                userProfileRepository.findForAdmin(
                        "%kim%",
                        EnumSet.of(UserStatus.ACTIVE),
                        adminPage()
                )
        ).getTotalElements());
    }

    @Test
    void publicDirectorySearchRunsAgainstPostgres() {
        assertEquals(0, assertDoesNotThrow(() ->
                userProfileRepository.findPublicProfiles(
                        "%cambodia%",
                        UserStatus.ACTIVE,
                        adminPage()
                )
        ).getTotalElements());
    }

    private PageRequest adminPage() {
        return PageRequest.of(
                0,
                20,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }
}
