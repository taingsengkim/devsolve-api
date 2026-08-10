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
}
