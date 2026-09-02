package kh.edu.istad.ite.devsoleapi.feature.recognition.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * One researcher on a program's or an organization's hall of thanks.
 *
 * <p>Counts only, and no reputation. Reputation is earned when a report is
 * resolved, is priced by the platform, and spans every organization; printing
 * a slice of it beside one organization's own thanks would read as though that
 * organization had awarded it. What an organization decides is who it credits,
 * and that is what this says.
 *
 * @param rank         position on this page's board, counted from the top of
 *                     the first page. Positional, so ties get consecutive
 *                     numbers rather than sharing one
 * @param recognitions how many times this researcher has been thanked here
 * @param bySeverity   how many of those were for findings at each severity, so
 *                     a card can show depth rather than volume alone.
 *                     Severities nobody was thanked for are absent
 */
public record ThanksResponse(

        Integer rank,

        UUID id,

        String username,

        String fullName,

        String avatarUrl,

        String country,

        long recognitions,

        Map<Severity, Long> bySeverity,

        LocalDateTime lastThankedAt
) {
}
