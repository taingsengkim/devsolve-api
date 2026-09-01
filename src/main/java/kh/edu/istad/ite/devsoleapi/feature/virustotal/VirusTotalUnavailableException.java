package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/**
 * VirusTotal could not produce a verdict: it was unreachable, rate limited,
 * not configured, or still analysing when the caller ran out of polls.
 *
 * <p>Deliberately distinct from a MALICIOUS or SUSPICIOUS verdict, which is
 * VirusTotal answering. Only this one is safe to fail open on. An upload path
 * that treats "the scanner is down" the same as "this file is a virus" hands
 * VirusTotal's availability — and, on a public key, its per-minute quota — a
 * veto over every upload the platform accepts.
 */
public class VirusTotalUnavailableException extends ResponseStatusException {

    public VirusTotalUnavailableException(
            HttpStatusCode status,
            String reason
    ) {
        super(status, reason);
    }
}
