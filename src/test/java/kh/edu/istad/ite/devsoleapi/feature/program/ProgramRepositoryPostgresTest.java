package kh.edu.istad.ite.devsoleapi.feature.program;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create"
})
class ProgramRepositoryPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ProgramRepository programRepository;

    @Test
    void publicListingQueryAcceptsAbsentAndPresentFilters() {
        assertEquals(0, assertDoesNotThrow(() -> search(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "publishedAt",
                "DESC"
        )).getTotalElements());

        assertEquals(0, assertDoesNotThrow(() -> search(
                UUID.randomUUID(),
                "bounty",
                true,
                "%acme%",
                new BigDecimal("100.00"),
                new BigDecimal("1000.00"),
                "api",
                "critical",
                "technology",
                "cambodia",
                "followerCount",
                "DESC"
        )).getTotalElements());
    }

    @Test
    void everySupportedSortExecutesAgainstPostgres() {
        List<String> properties = List.of(
                "id",
                "publishedAt",
                "createdAt",
                "updatedAt",
                "name",
                "handle",
                "minimumBounty",
                "maximumBounty",
                "viewCount",
                "followerCount",
                "totalSubmissions"
        );

        for (String property : properties) {
            assertDoesNotThrow(() -> search(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    property,
                    "ASC"
            ));
        }
    }

    private org.springframework.data.domain.Page<Program> search(
            UUID organizationId,
            String engagementType,
            Boolean offersBounties,
            String queryPattern,
            BigDecimal minimumBounty,
            BigDecimal maximumBounty,
            String assetType,
            String maxSeverity,
            String industry,
            String country,
            String sortProperty,
            String sortDirection
    ) {
        return programRepository.searchPublicPrograms(
                organizationId,
                engagementType,
                offersBounties,
                queryPattern,
                minimumBounty,
                maximumBounty,
                assetType,
                maxSeverity,
                industry,
                country,
                sortProperty,
                sortDirection,
                PageRequest.of(0, 20)
        );
    }
}
