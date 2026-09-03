package kh.edu.istad.ite.devsoleapi.config;

import kh.edu.istad.ite.devsoleapi.feature.category.CategoryScope;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.CachedProblem;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemListingSlice;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemSeverity;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemType;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.SdlcPhase;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramListingSlice;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramOrganizationDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.PublicProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardResponse;
import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardSlice;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseDetailParts;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseListingSlice;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseTagResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * With {@code app.redis.enabled=false} the suite never builds a cache, so a
 * serializer that cannot read back what it wrote would first show up as a
 * ClassCastException on a live cache hit. These round-trips stand in for that.
 */
class CacheConfigTest {

    private final SerializationPair<List<CategoryResponse>> serializer =
            CacheConfig.categoryListSerializer();

    @Test
    void roundTripsACategoryListWithEveryFieldIntact() {
        CategoryResponse original = new CategoryResponse(
                UUID.randomUUID(),
                "Web Security",
                "web-security",
                CategoryScope.PROBLEM,
                "Anything that speaks HTTP",
                "https://example.test/icons/web.png",
                3,
                true,
                LocalDateTime.of(2026, 8, 29, 10, 15, 30, 123_000_000),
                LocalDateTime.of(2026, 8, 29, 11, 0, 0)
        );

        List<CategoryResponse> restored =
                serializer.read(serializer.write(List.of(original)));

        assertNotNull(restored);
        assertEquals(1, restored.size());
        // Records compare by value, so this covers the UUID, the enum and both
        // LocalDateTimes — the types most likely to come back as something else.
        assertEquals(original, restored.getFirst());
    }

    @Test
    void roundTripsNullFieldsRatherThanDroppingThem() {
        CategoryResponse sparse = new CategoryResponse(
                UUID.randomUUID(),
                "Untitled",
                "untitled",
                CategoryScope.SHOWCASE,
                null,
                null,
                null,
                null,
                null,
                null
        );

        List<CategoryResponse> restored =
                serializer.read(serializer.write(List.of(sparse)));

        assertEquals(List.of(sparse), restored);
    }

    @Test
    void roundTripsAnEmptyList() {
        assertEquals(List.of(), serializer.read(serializer.write(List.of())));
    }

    @Test
    void roundTripsALeaderboardSliceWithItsTotalIntact() {
        SerializationPair<LeaderboardSlice> leaderboard =
                CacheConfig.leaderboardSliceSerializer();

        LeaderboardSlice original = new LeaderboardSlice(
                List.of(new LeaderboardResponse(
                        7,
                        UUID.randomUUID(),
                        "sokdara",
                        "Sok Dara",
                        "https://example.test/avatars/dara.png",
                        "KH",
                        420,
                        12,
                        9,
                        2,
                        3
                )),
                58
        );

        LeaderboardSlice restored =
                leaderboard.read(leaderboard.write(original));

        assertNotNull(restored);
        assertEquals(original, restored);
        // The total drives how many pages the caller thinks exist, so a lost
        // one is a silently truncated leaderboard rather than a visible error.
        assertEquals(58, restored.totalElements());
    }

    @Test
    void roundTripsShowcaseDetailPartsKeepingStepOrder() {
        SerializationPair<ShowcaseDetailParts> showcase =
                CacheConfig.showcaseDetailSerializer();

        ShowcaseDetailParts original = new ShowcaseDetailParts(
                List.of(new ShowcaseTagResponse(
                        UUID.randomUUID(),
                        "Reverse Engineering",
                        "reverse-engineering"
                )),
                List.of(
                        new ShowcaseStepResponse(
                                UUID.randomUUID(),
                                1,
                                "Recon",
                                "Map the surface",
                                "nmap -sV target",
                                "https://example.test/1.png",
                                null,
                                LocalDateTime.of(2026, 8, 30, 9, 0),
                                LocalDateTime.of(2026, 8, 30, 9, 30)
                        ),
                        new ShowcaseStepResponse(
                                UUID.randomUUID(),
                                2,
                                "Exploit",
                                null,
                                null,
                                null,
                                null,
                                LocalDateTime.of(2026, 8, 30, 10, 0),
                                null
                        )
                )
        );

        ShowcaseDetailParts restored =
                showcase.read(showcase.write(original));

        assertNotNull(restored);
        assertEquals(original, restored);
        // Steps are ordered by step number in the query, and the reader relies
        // on that order rather than re-sorting.
        assertEquals(
                List.of(1, 2),
                restored.steps().stream()
                        .map(ShowcaseStepResponse::stepNumber)
                        .toList()
        );
    }

    @Test
    void roundTripsAShowcaseListingSliceWithItsTotalIntact() {
        SerializationPair<ShowcaseListingSlice> listing =
                CacheConfig.showcaseListingSerializer();

        ShowcaseListingSlice original = new ShowcaseListingSlice(
                List.of(ShowCasesSummaryResponse.builder()
                        .id(UUID.randomUUID())
                        .authorName("Sok Dara")
                        .categoryName("Web Security")
                        .title("Breaking a JWT")
                        .overview("How a weak secret fell")
                        .reviewStatus(ReviewStatus.APPROVED)
                        .viewCount(94)
                        .commentCount(0)
                        .tags(List.of(new ShowcaseTagResponse(
                                UUID.randomUUID(), "JWT", "jwt"
                        )))
                        .createdAt(LocalDateTime.of(2026, 8, 30, 8, 0))
                        .build()),
                31
        );

        ShowcaseListingSlice restored = listing.read(listing.write(original));

        assertNotNull(restored);
        assertEquals(original, restored);
        assertEquals(31, restored.totalElements());
    }

    @Test
    void roundTripsShowcaseDetailPartsWithNoTagsOrSteps() {
        SerializationPair<ShowcaseDetailParts> showcase =
                CacheConfig.showcaseDetailSerializer();

        ShowcaseDetailParts bare =
                new ShowcaseDetailParts(List.of(), List.of());

        assertEquals(bare, showcase.read(showcase.write(bare)));
    }

    @Test
    void roundTripsAProgramListingSliceWithItsNestedRecordsIntact() {
        SerializationPair<ProgramListingSlice> listing =
                CacheConfig.programListingSerializer();

        ProgramListingSlice original = new ProgramListingSlice(
                List.of(new ProgramSummaryResponseDto(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Acme Security",
                        new ProgramOrganizationDto(
                                UUID.randomUUID(),
                                "Acme Security",
                                "acme-security",
                                "https://example.test/logo.png",
                                "https://acme.test",
                                "We break things",
                                Industry.TECHNOLOGY,
                                "KH",
                                LocalDateTime.of(2026, 8, 20, 9, 0)
                        ),
                        "acme-vdp",
                        "Acme VDP",
                        "Report anything you find",
                        EngagementType.BOUNTY,
                        true,
                        new BigDecimal("50.00"),
                        new BigDecimal("5000.00"),
                        List.of(new ProgramAssetResponseDto(
                                UUID.randomUUID(),
                                AssetType.WILDCARD,
                                "*.acme.test",
                                "Main site",
                                true,
                                Severity.CRITICAL
                        )),
                        1_204L,
                        87L,
                        42L,
                        LocalDateTime.of(2026, 8, 21, 10, 0),
                        LocalDateTime.of(2026, 8, 20, 8, 0),
                        LocalDateTime.of(2026, 8, 22, 11, 30)
                )),
                31
        );

        ProgramListingSlice restored = listing.read(listing.write(original));

        assertNotNull(restored);
        // Records compare by value, so this reaches the nested organization
        // and asset records, the enums and both BigDecimals — the types most
        // likely to come back as a LinkedHashMap or a double.
        assertEquals(original, restored);
        assertEquals(31, restored.totalElements());
        assertEquals(
                new BigDecimal("5000.00"),
                restored.content().getFirst().maximumBounty()
        );
    }

    @Test
    void roundTripsAProgramListingSliceWithNoRows() {
        SerializationPair<ProgramListingSlice> listing =
                CacheConfig.programListingSerializer();
        ProgramListingSlice empty = new ProgramListingSlice(List.of(), 0);

        assertEquals(empty, listing.read(listing.write(empty)));
    }

    @Test
    void roundTripsAPublicProgramKeepingItsCountsAndNullSections() {
        SerializationPair<PublicProgramResponseDto> program =
                CacheConfig.programDetailSerializer();

        PublicProgramResponseDto original = new PublicProgramResponseDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Acme Security",
                null,
                "acme-vdp",
                "Acme VDP",
                "Report anything you find",
                EngagementType.BOUNTY,
                ProgramState.ACTIVE,
                SubmissionState.APPROVED,
                Visibility.PUBLIC,
                "Do not test production",
                null,
                null,
                null,
                true,
                new BigDecimal("50.00"),
                new BigDecimal("5000.00"),
                List.of(),
                List.of(),
                1_204L,
                87L,
                19L,
                42L,
                LocalDateTime.of(2026, 8, 21, 10, 0),
                LocalDateTime.of(2026, 8, 20, 8, 0),
                LocalDateTime.of(2026, 8, 22, 11, 30)
        );

        PublicProgramResponseDto restored =
                program.read(program.write(original));

        assertNotNull(restored);
        assertEquals(original, restored);
        // These four are the reason this cache has a short TTL rather than a
        // long one, so a serializer that dropped them would be quietly wrong.
        assertEquals(1_204L, restored.viewCount());
        assertEquals(87L, restored.followerCount());
        assertEquals(19L, restored.totalResearchers());
        assertEquals(42L, restored.totalSubmissions());
    }

    private static CachedProblem cachedProblem(UUID authorId) {
        return new CachedProblem(
                new ProblemResponse(
                        UUID.randomUUID(),
                        new ProblemResponse.AuthorSummary(
                                authorId,
                                "Sok Dara",
                                "https://example.test/avatar.png",
                                420
                        ),
                        new ProblemResponse.CategorySummary(
                                UUID.randomUUID(),
                                "Web Security",
                                "web-security",
                                CategoryScope.PROBLEM
                        ),
                        "JWT verification fails",
                        "The token is rejected",
                        ProblemType.BUG,
                        SdlcPhase.DEVELOPMENT,
                        ProblemSeverity.HIGH,
                        "It should verify",
                        "It throws",
                        List.of("Sign a token", "Verify it"),
                        List.of(new ProblemResponse.EnvironmentSummary(
                                "Java", "21"
                        )),
                        "Tried rotating the key",
                        "SignatureException",
                        "https://github.test/acme/api",
                        ProblemStatus.PUBLISHED,
                        94L,
                        List.of(new ProblemResponse.TechnologySummary(
                                UUID.randomUUID(), "Spring", "6.2"
                        )),
                        List.of(new ProblemResponse.TagSummary(
                                UUID.randomUUID(), "JWT", "jwt"
                        )),
                        List.of(new ProblemResponse.AttachmentSummary(
                                UUID.randomUUID(),
                                "stack.txt",
                                "text/plain",
                                1_024L,
                                UUID.randomUUID(),
                                Instant.parse("2026-08-30T09:00:00Z"),
                                "https://example.test/stack.txt"
                        )),
                        List.of("profanity"),
                        0L,
                        0L,
                        0L,
                        0L,
                        List.of(UUID.randomUUID()),
                        false,
                        null,
                        false,
                        false,
                        false,
                        Instant.parse("2026-08-29T10:00:00Z"),
                        null,
                        7L,
                        LocalDateTime.of(2026, 8, 29, 9, 0),
                        LocalDateTime.of(2026, 8, 30, 11, 30)
                ),
                authorId
        );
    }

    @Test
    void roundTripsAProblemListingSliceThroughEveryNestedRecord() {
        SerializationPair<ProblemListingSlice> listing =
                CacheConfig.problemListingSerializer();

        ProblemListingSlice original = new ProblemListingSlice(
                List.of(cachedProblem(UUID.randomUUID())),
                17
        );

        ProblemListingSlice restored = listing.read(listing.write(original));

        assertNotNull(restored);
        // Reaches the author, category, environment, technology, tag and
        // attachment records, the enums, and the Instants — everything most
        // likely to come back as a LinkedHashMap or a string.
        assertEquals(original, restored);
        assertEquals(17, restored.totalElements());
    }

    @Test
    void roundTripsACachedProblemKeepingTheAuthorItCarriesOutOfBand() {
        SerializationPair<CachedProblem> detail =
                CacheConfig.problemDetailSerializer();
        UUID authorId = UUID.randomUUID();

        CachedProblem restored =
                detail.read(detail.write(cachedProblem(authorId)));

        assertNotNull(restored);
        // The out-of-band author id is what decides who may edit; losing it
        // would silently strip an author of their own permissions.
        assertEquals(authorId, restored.authorId());
    }

    @Test
    void aCachedProblemNeverCarriesViewerState() {
        SerializationPair<CachedProblem> detail =
                CacheConfig.problemDetailSerializer();

        CachedProblem restored = detail.read(
                detail.write(cachedProblem(UUID.randomUUID()))
        );
        ProblemResponse response = restored.response();

        // What a shared cache must never hold. If one of these ever comes back
        // set, a reader is being handed someone else's vote or permissions.
        assertFalse(response.isBookmarkedByViewer());
        assertNull(response.viewerVote());
        assertFalse(response.canEdit());
        assertFalse(response.canDelete());
        assertFalse(response.canAcceptSolution());
    }

    /**
     * The deepest record in any cache here — seven nested types, a null change
     * percentage, an enum and eight BigDecimals. A serializer that lost the
     * nesting would hand the dashboard a page of LinkedHashMaps, and one that
     * turned the null into 0 would report a first report in a quiet window as
     * no growth at all.
     */
    @Test
    void roundTripsOrganizationAnalyticsThroughEveryNestedRecord() {
        SerializationPair<OrganizationAnalyticsResponse> analytics =
                CacheConfig.organizationAnalyticsSerializer();

        OrganizationAnalyticsResponse original =
                new OrganizationAnalyticsResponse(
                        UUID.randomUUID(),
                        "Acme Corp",
                        "6m",
                        null,
                        Instant.parse("2026-09-03T10:00:00Z"),
                        new OrganizationAnalyticsResponse.KpiSummary(
                                new OrganizationAnalyticsResponse.CountMetric(
                                        248L, new BigDecimal("14.2"), "up"
                                ),
                                new OrganizationAnalyticsResponse.AcceptedMetric(
                                        168L,
                                        new BigDecimal("67.7"),
                                        null,
                                        "up"
                                ),
                                new OrganizationAnalyticsResponse.RejectedMetric(
                                        45L,
                                        new BigDecimal("18.1"),
                                        new BigDecimal("-4.2"),
                                        "down"
                                ),
                                new OrganizationAnalyticsResponse.BountyMetric(
                                        new BigDecimal("54250.00"),
                                        "USD",
                                        new BigDecimal("12.0"),
                                        "up"
                                ),
                                4850L,
                                new OrganizationAnalyticsResponse.CountMetric(
                                        82L, new BigDecimal("15.0"), "up"
                                ),
                                new OrganizationAnalyticsResponse.SlaMetrics(
                                        new BigDecimal("14.5"),
                                        new BigDecimal("11.2"),
                                        new BigDecimal("94.5"),
                                        24
                                )
                        ),
                        List.of(new OrganizationAnalyticsResponse
                                .SubmissionTrendPoint(
                                "2026-03",
                                "Mar 2026",
                                35L,
                                24L,
                                20L,
                                6L,
                                new BigDecimal("6500.00")
                        )),
                        new OrganizationAnalyticsResponse.SeverityDistribution(
                                severityBand(18L, "7.3", "2500.00"),
                                severityBand(52L, "21.0", "1000.00"),
                                severityBand(86L, "34.7", "350.00"),
                                severityBand(72L, "29.0", "100.00"),
                                severityBand(20L, "8.0", "0.00")
                        ),
                        List.of(new OrganizationAnalyticsResponse
                                .VulnerabilityCategory(
                                "CWE-284",
                                "Improper Access Control",
                                48L,
                                new BigDecimal("19.3"),
                                8L
                        )),
                        List.of(new OrganizationAnalyticsResponse.TargetedAsset(
                                "api.acme.test",
                                AssetType.API,
                                56L,
                                7L,
                                18L,
                                new BigDecimal("18500.00")
                        )),
                        List.of(new OrganizationAnalyticsResponse
                                .ResearcherStanding(
                                UUID.randomUUID(),
                                "0xsec_hunter",
                                "Alex Rivera",
                                null,
                                1,
                                18L,
                                4L,
                                new BigDecimal("14500.00"),
                                720L
                        ))
                );

        OrganizationAnalyticsResponse restored =
                analytics.read(analytics.write(original));

        assertNotNull(restored);
        assertEquals(original, restored);
        assertNull(
                restored.kpiSummary().acceptedReports().changePercentage()
        );
        assertEquals(
                new BigDecimal("54250.00"),
                restored.kpiSummary().totalBountiesPaid().amount()
        );
        assertEquals(
                AssetType.API,
                restored.topTargetedAssets().getFirst().assetType()
        );
    }

    private static OrganizationAnalyticsResponse.SeverityBand severityBand(
            long count,
            String share,
            String bounty
    ) {
        return new OrganizationAnalyticsResponse.SeverityBand(
                count,
                new BigDecimal(share),
                new BigDecimal(bounty)
        );
    }

    @Test
    void roundTripsALeaderboardSliceWithNoRows() {
        SerializationPair<LeaderboardSlice> leaderboard =
                CacheConfig.leaderboardSliceSerializer();

        LeaderboardSlice empty = new LeaderboardSlice(List.of(), 0);

        assertEquals(empty, leaderboard.read(leaderboard.write(empty)));
    }
}
