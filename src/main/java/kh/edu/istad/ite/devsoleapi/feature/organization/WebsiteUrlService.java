package kh.edu.istad.ite.devsoleapi.feature.organization;

public interface WebsiteUrlService {
    String normalize(String websiteUrl);

    String extractDomain(String websiteUrl);

    boolean matchesEmailDomain(String email, String websiteUrl);
}
