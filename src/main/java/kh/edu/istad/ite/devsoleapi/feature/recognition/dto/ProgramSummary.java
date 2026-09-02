package kh.edu.istad.ite.devsoleapi.feature.recognition.dto;

import java.util.UUID;

/**
 * Which program a thank-you came from, and which organization runs it.
 *
 * <p>Carried rather than left as a bare id. A hall of thanks reads as "who
 * credited me, and for what" — an id renders as nothing, and resolving it
 * client-side is a request per row on a page that is mostly rows.
 *
 * @param organizationName the name the credit is attributed to on a card;
 *                         the program is what was tested, the organization is
 *                         who said thank you, and a researcher's profile shows
 *                         both
 * @param organizationLogoUrl the organization's mark, so a card can show who
 *                         thanked somebody rather than only naming them. Null
 *                         until the organization uploads one, so a client
 *                         still needs a fallback
 */
public record ProgramSummary(

        UUID id,

        String name,

        String handle,

        UUID organizationId,

        String organizationName,

        String organizationSlug,

        String organizationLogoUrl
) {
}
