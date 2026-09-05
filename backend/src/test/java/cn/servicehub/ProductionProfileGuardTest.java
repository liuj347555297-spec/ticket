package cn.servicehub;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.servicehub.config.ProductionProfileGuard;
import cn.servicehub.config.SecurityProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionProfileGuardTest {
    @Test
    void productionRejectsLocalDevelopmentProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql", "prod", "local-dev");
        var guard = new ProductionProfileGuard(environment, new SecurityProperties(List.of("https://portal.example"), false, false));
        assertThrows(IllegalStateException.class, guard::afterPropertiesSet);
    }

    @Test
    void productionRejectsDirectTestIdentities() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql", "prod");
        var guard = new ProductionProfileGuard(environment, new SecurityProperties(List.of("https://portal.example"), true, false));
        assertThrows(IllegalStateException.class, guard::afterPropertiesSet);
    }

    @Test
    void hardenedProductionCombinationIsAccepted() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("mysql", "prod");
        var guard = new ProductionProfileGuard(environment, new SecurityProperties(List.of("https://portal.example"), false, false));
        assertDoesNotThrow(guard::afterPropertiesSet);
    }
}
