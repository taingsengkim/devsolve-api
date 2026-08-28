package kh.edu.istad.ite.devsoleapi.feature.program;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ProgramControllerTest {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void mapsMyProgramDetailEndpoint() {
        boolean endpointExists = handlerMapping.getHandlerMethods()
                .entrySet()
                .stream()
                .anyMatch(entry -> entry.getKey()
                        .getPatternValues()
                        .contains("/api/v1/organizations/me/programs/{id}")
                        && entry.getKey()
                        .getMethodsCondition()
                        .getMethods()
                        .contains(RequestMethod.GET)
                        && entry.getValue()
                        .getBeanType()
                        .equals(ProgramController.class));

        assertTrue(endpointExists);
    }

    @Test
    void mapsCanonicalAndCompatibilityProgramSaveEndpoints() {
        assertTrue(hasProgramEndpoint(
                "/api/v1/organizations/me/programs/{id}",
                RequestMethod.PATCH
        ));
        assertTrue(hasProgramEndpoint(
                "/api/v1/organizations/me/programs/{id}",
                RequestMethod.PUT
        ));
        assertTrue(hasProgramEndpoint(
                "/api/v1/programs/{id}",
                RequestMethod.PATCH
        ));
        assertTrue(hasProgramEndpoint(
                "/api/v1/programs/{id}",
                RequestMethod.PUT
        ));
    }

    @Test
    void mapsCanonicalAndOrganizationScopedPublishEndpoints() {
        assertTrue(hasProgramEndpoint(
                "/api/v1/programs/{id}/publish",
                RequestMethod.PATCH
        ));
        assertTrue(hasProgramEndpoint(
                "/api/v1/organizations/me/programs/{id}/publish",
                RequestMethod.PATCH
        ));
    }

    /**
     * The literal segment has to win over {@code /{id}}, or "handle-available"
     * reaches the UUID converter instead of the check.
     */
    @Test
    void mapsHandleAvailabilityEndpoint() {
        assertTrue(hasProgramEndpoint(
                "/api/v1/organizations/me/programs/handle-available",
                RequestMethod.GET
        ));
    }

    @Test
    void mapsPublicProgramViewEndpoint() {
        boolean endpointExists = handlerMapping.getHandlerMethods()
                .entrySet()
                .stream()
                .anyMatch(entry -> entry.getKey()
                        .getPatternValues()
                        .contains("/api/v1/programs/{id}/views")
                        && entry.getKey()
                        .getMethodsCondition()
                        .getMethods()
                        .contains(RequestMethod.POST)
                        && entry.getValue()
                        .getBeanType()
                        .equals(ProgramController.class));

        assertTrue(endpointExists);
    }

    @Test
    void mapsAdminProgramRemovalEndpoint() {
        assertTrue(hasEndpoint(
                "/api/v1/admin/programs/{id}",
                RequestMethod.DELETE,
                ProgramAdminController.class
        ));
    }

    private boolean hasProgramEndpoint(
            String path,
            RequestMethod method
    ) {
        return hasEndpoint(path, method, ProgramController.class);
    }

    private boolean hasEndpoint(
            String path,
            RequestMethod method,
            Class<?> beanType
    ) {
        return handlerMapping.getHandlerMethods()
                .entrySet()
                .stream()
                .anyMatch(entry -> entry.getKey()
                        .getPatternValues()
                        .contains(path)
                        && entry.getKey()
                        .getMethodsCondition()
                        .getMethods()
                        .contains(method)
                        && entry.getValue()
                        .getBeanType()
                        .equals(beanType));
    }
}
