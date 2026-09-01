package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;
import kh.edu.istad.ite.devsoleapi.feature.recognition.Recognition;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Weakness;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class HacktivityMapper {

    /**
     * Every amount the platform has stored. There is no per-payout currency
     * column, so this is a statement of that fact rather than a lookup — when
     * a second currency becomes possible it becomes a column and this
     * constant goes away.
     */
    static final String CURRENCY = "USD";

    /**
     * @param payout the totalled rewards on this row's report, or null when
     *               the report was never paid
     */
    public HacktivityResponse toResponse(
            Hacktivity hacktivity,
            HacktivityRepository.ReportPayout payout
    ) {

        var user = hacktivity.getUser();
        var organization = hacktivity.getOrganization();
        var report = hacktivity.getReport();
        var recognition = hacktivity.getRecognition();
        var program = hacktivity.getProgram();

        return new HacktivityResponse(

                hacktivity.getId(),

                hacktivity.getEventType(),

                new HacktivityResponse.User(
                        user.getId(),
                        user.getUsername(),
                        user.getFullName(),
                        user.getAvatarUrl(),
                        user.getReputation()
                ),

                new HacktivityResponse.Organization(
                        organization.getId(),
                        organization.getName(),
                        organization.getSlug(),
                        organization.getLogoUrl()
                ),

                new HacktivityResponse.Program(
                        program.getId(),
                        program.getName(),
                        program.getHandle()
                ),

                new HacktivityResponse.Report(
                        report.getId(),
                        publishableTitle(report),
                        // Not .name(): the enum itself goes on the wire, so a
                        // client cannot be handed a casing that drifts.
                        report.getSeverity(),
                        report.getDisclosureStatus(),
                        toWeakness(report.getWeakness())
                ),

                toRecognition(recognition),

                toReward(payout),

                toInstant(hacktivity.getCreatedAt())
        );
    }

    /**
     * The report title, but only once the report is actually disclosed.
     *
     * <p>A row reaches the feed because it was recognised, which happens as
     * soon as a triager credits the finder — typically long before, and often
     * without ever, the report being disclosed. The feed is served to anonymous
     * callers, so returning the raw title here publishes the one-line summary
     * of a live vulnerability to whoever asks: "SQL injection in the payments
     * export" names the bug, the component, and the company on a finding the
     * engineering team may not have shipped a fix for.
     *
     * <p>Null rather than a placeholder so a client cannot print it by
     * accident, and {@code disclosureStatus} still travels beside it so the
     * card can say why the title is missing.
     */
    private String publishableTitle(Report report) {

        return report.getDisclosureStatus() == DisclosureStatus.DISCLOSED
                ? report.getTitle()
                : null;
    }

    /**
     * Null on a row that is about a resolution or a disclosure rather than a
     * recognition. {@code eventType} says which, so a card does not have to
     * infer it from what came back empty.
     */
    private HacktivityResponse.Recognition toRecognition(
            Recognition recognition
    ) {
        if (recognition == null) {
            return null;
        }

        return new HacktivityResponse.Recognition(
                recognition.getId(),
                recognition.getTitle(),
                recognition.getDescription()
        );
    }

    private HacktivityResponse.Weakness toWeakness(Weakness weakness) {

        if (weakness == null) {
            return null;
        }

        return new HacktivityResponse.Weakness(
                weakness.getCweId(),
                weakness.getName()
        );
    }

    /**
     * A report with no rewards has no reward object at all, rather than one
     * reading zero: a card should be able to tell "not paid" from "paid
     * nothing".
     */
    private HacktivityResponse.Reward toReward(
            HacktivityRepository.ReportPayout payout
    ) {

        if (payout == null) {
            return null;
        }

        BigDecimal amount = payout.getAmount();
        Long points = payout.getPoints();

        return new HacktivityResponse.Reward(
                amount,
                amount == null ? null : CURRENCY,
                points == null ? null : points.intValue()
        );
    }

    /**
     * The column is a local timestamp written by the application with
     * {@code LocalDateTime.now()}, so it is in the server's zone. Reading it
     * back through that same zone is what makes the instant true; stamping
     * {@code Z} on it instead would be correct only by the accident of the
     * container running in UTC, and silently wrong by the offset anywhere
     * else.
     */
    private Instant toInstant(LocalDateTime value) {

        return value == null
                ? null
                : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
