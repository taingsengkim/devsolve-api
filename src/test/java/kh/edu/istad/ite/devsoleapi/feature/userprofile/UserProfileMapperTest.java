package kh.edu.istad.ite.devsoleapi.feature.userprofile;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import org.keycloak.representations.idm.UserRepresentation;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserProfileMapperTest {

    private final UserProfileMapper mapper = Mappers.getMapper(UserProfileMapper.class);

    @Test
    void updateMapsEditableFieldsWithoutChangingServerManagedFields() {
        UUID id = UUID.randomUUID();
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setEmail("member@example.com");
        profile.setFullName("Original Name");
        profile.setStatus(UserStatus.ACTIVE);
        profile.setReputation(40);

        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "Updated",
                "Name",
                "Security researcher",
                "+85512345678",
                "https://cdn.example.com/avatar.png",
                LocalDate.of(2000, 1, 1),
                GenderStatus.OTHER,
                "Cambodia"
        );

        mapper.mapUpdateUserProfileRequestToUserProfile(request, profile);

        assertEquals(id, profile.getId());
        assertEquals("member@example.com", profile.getEmail());
        assertEquals(UserStatus.ACTIVE, profile.getStatus());
        assertEquals(40, profile.getReputation());
        assertEquals("Original Name", profile.getFullName());
        assertEquals("Security researcher", profile.getBiography());
        assertEquals("+85512345678", profile.getPhone());
        assertEquals("https://cdn.example.com/avatar.png", profile.getAvatarUrl());
        assertEquals(LocalDate.of(2000, 1, 1), profile.getDateOfBirth());
        assertEquals(GenderStatus.OTHER, profile.getGender());
        assertEquals("Cambodia", profile.getCountry());
    }

    @Test
    void updateIgnoresNullFields() {
        UserProfile profile = new UserProfile();
        profile.setFullName("Existing Name");
        profile.setCountry("Cambodia");

        mapper.mapUpdateUserProfileRequestToUserProfile(
                new UpdateUserProfileRequest(null, null, null, null, null, null, null, null),
                profile
        );

        assertEquals("Existing Name", profile.getFullName());
        assertEquals("Cambodia", profile.getCountry());
        assertNull(profile.getBiography());
    }

    @Test
    void responseContainsProfileStatistics() {
        UUID id = UUID.randomUUID();
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setEmail("member@example.com");
        profile.setFullName("Member Name");
        profile.setStatus(UserStatus.ACTIVE);
        profile.setReputation(120);
        profile.setTotalReports(8);
        profile.setValidReports(5);
        profile.setCriticalReports(2);
        profile.setRecognitionCount(3);

        UserRepresentation keycloakUser = new UserRepresentation();
        keycloakUser.setEmail("member@example.com");
        keycloakUser.setFirstName("Member");
        keycloakUser.setLastName("Name");

        UserProfileResponse response = mapper.toUserProfileResponse(keycloakUser, profile);

        assertEquals(id, response.id());
        assertEquals("Member", response.firstName());
        assertEquals("Name", response.lastName());
        assertEquals("Member Name", response.fullName());
        assertEquals(120, response.reputation());
        assertEquals(8, response.totalReports());
        assertEquals(5, response.validReports());
        assertEquals(2, response.criticalReports());
        assertEquals(3, response.recognitionCount());
    }
}
