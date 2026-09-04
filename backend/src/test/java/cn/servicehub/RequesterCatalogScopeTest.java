package cn.servicehub;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.servicehub.catalog.config.*;
import cn.servicehub.ticket.domain.TicketType;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RequesterCatalogScopeTest {
    private static final String ACTOR = "iam-u-local-requester";
    private static final String ORG = "ORG-LOCAL-IT";
    @Autowired MockMvc mvc;
    @Autowired FormConfigurationRepository forms;
    @Autowired cn.servicehub.ticket.domain.TicketRepository tickets;

    @Test void directRequesterReadsAndCreatesCannotBypassOrganizationOrPublication() throws Exception {
        form("SC-closed-foreign", List.of("ORG-OTHER"), FormConfigurationStatus.PUBLISHED);
        form("SC-closed-empty", List.of(), FormConfigurationStatus.PUBLISHED);
        form("SC-closed-draft", List.of(ORG), FormConfigurationStatus.DRAFT);
        form("SC-closed-review", List.of(ORG), FormConfigurationStatus.PENDING_REVIEW);
        form("SC-closed-retired", List.of(ORG), FormConfigurationStatus.RETIRED);
        mvc.perform(get("/api/v1/service-catalog/items").param("q", "SC-closed-").with(user(ACTOR).roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
        for (String suffix : List.of("foreign", "empty", "draft", "review", "retired")) {
            String id = "SC-closed-" + suffix;
            for (String ending : List.of("", "/form")) {
                mvc.perform(get("/api/v1/service-catalog/items/" + id + ending).with(user(ACTOR).roles("REQUESTER")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", is("SERVICE_CATALOG_INVALID")));
            }
            mvc.perform(get("/api/v1/service-catalog/dictionaries/BROWSER/entries")
                    .param("serviceCatalogItemId", id).param("formVersion", "3").param("fieldCode", "browser")
                    .with(user(ACTOR).roles("REQUESTER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", is("SERVICE_CATALOG_INVALID")));
            mvc.perform(post("/api/v1/tickets").with(user(ACTOR).roles("REQUESTER")).with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(payload(id, 3)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", is("SERVICE_CATALOG_INVALID")));
        }
        mvc.perform(get("/api/v1/service-catalog/items").with(user("iam-u-missing").roles("REQUESTER")))
            .andExpect(status().isForbidden());
    }

    @Test void validUnmappedServiceStillCreatesWithoutSystemAndRetirementDoesNotChangeHistory() throws Exception {
        String id = "SC-authorized-request";
        form(id, List.of(ORG), FormConfigurationStatus.PUBLISHED);
        mvc.perform(get("/api/v1/service-catalog/items/" + id + "/form").with(user(ACTOR).roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.formVersion", is(3)));
        String body = mvc.perform(post("/api/v1/tickets").with(user(ACTOR).roles("REQUESTER")).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(payload(id, 3)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.serviceCatalogFormVersion", is(3)))
            .andReturn().getResponse().getContentAsString();
        String ticketId = JsonPath.read(body, "$.id");
        form(id, List.of(ORG), FormConfigurationStatus.RETIRED);
        mvc.perform(post("/api/v1/tickets").with(user(ACTOR).roles("REQUESTER")).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(payload(id, 3)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", is("SERVICE_CATALOG_INVALID")));
        mvc.perform(get("/api/v1/tickets/" + ticketId).with(user(ACTOR).roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.serviceCatalogFormVersion", is(3)))
            .andExpect(jsonPath("$.serviceCatalogItem.id", is(id)));
        org.junit.jupiter.api.Assertions.assertEquals("Chrome", tickets.findById(ticketId).orElseThrow().structuredFields().get("browser"));
    }

    @Test void managedShadowNeverReopensLegacySeedThroughDirectEndpoints() throws Exception {
        String id = "SC-browser-performance";
        mvc.perform(get("/api/v1/service-catalog/items/" + id + "/form").with(user(ACTOR).roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.formVersion", is(1)));
        for (FormConfigurationStatus state : List.of(FormConfigurationStatus.PUBLISHED, FormConfigurationStatus.DRAFT, FormConfigurationStatus.RETIRED)) {
            form(id, List.of("ORG-OTHER"), state);
            mvc.perform(get("/api/v1/service-catalog/items/" + id + "/form").with(user(ACTOR).roles("REQUESTER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", is("SERVICE_CATALOG_INVALID")));
            // The old seed version and old field shape must not reopen the bypass either.
            mvc.perform(post("/api/v1/tickets").with(user(ACTOR).roles("REQUESTER")).with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(payload(id, 1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", is("SERVICE_CATALOG_INVALID")));
        }
    }

    private String payload(String id, int version) {
        return "{\"serviceCatalogItemId\":\"" + id + "\",\"serviceCatalogFormVersion\":" + version
            + ",\"type\":\"INCIDENT\",\"title\":\"门户权限回归\",\"description\":\"实际申请内容回归验证\",\"structuredFields\":{\"browser\":\"Chrome\"},\"tags\":[]}";
    }
    private void form(String id, List<String> orgs, FormConfigurationStatus state) {
        long previous = forms.findById(id).map(ManagedFormConfiguration::version).orElse(0L);
        Instant now = Instant.now();
        forms.save(new ManagedFormConfiguration(id, id.replace('-', '_').toUpperCase(), id, "受控目录", TicketType.INCIDENT, "BROWSER",
            orgs, List.of(new ConfiguredFormField("browser", "浏览器", ConfigurableFormFieldType.SINGLE_SELECT, true, null, null, null,
                "BROWSER", 1, List.of(), List.of())), new TagPolicy(true, false, 10, List.of()), state, previous + 1, 3,
            "test-scope-schema", "test fixture", ACTOR, ACTOR, now, now, state == FormConfigurationStatus.PUBLISHED ? now : null), previous);
    }
}
