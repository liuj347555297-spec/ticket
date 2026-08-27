package cn.servicehub;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ServiceCatalogControllerTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void catalogIsAuthenticatedReadOnlyAndOnlyExposesPublishedDefinitions() throws Exception {
        mockMvc.perform(get("/api/v1/service-catalog/items")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/service-catalog/items").with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id", is("SC-browser-performance")));
    }

    @Test
    void publishedFormAndDictionaryEntriesRequireTheDeclaredCatalogFieldAndVersion() throws Exception {
        mockMvc.perform(get("/api/v1/service-catalog/items/SC-browser-performance/form")
                .with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.formVersion", is(1)))
            .andExpect(jsonPath("$.formSchemaHash").isNotEmpty())
            .andExpect(jsonPath("$.fields[0].code", is("browser")));

        mockMvc.perform(get("/api/v1/service-catalog/dictionaries/BROWSER/entries")
                .param("serviceCatalogItemId", "SC-browser-performance").param("formVersion", "1").param("fieldCode", "browser")
                .with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.formVersion", is(1)))
            .andExpect(jsonPath("$.items[0].code", is("Chrome")));

        mockMvc.perform(get("/api/v1/service-catalog/dictionaries/BROWSER/entries")
                .param("serviceCatalogItemId", "SC-browser-performance").param("formVersion", "2").param("fieldCode", "browser")
                .with(user("iam-u-1001").roles("REQUESTER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("SERVICE_CATALOG_INVALID")));
    }

    @Test
    void ticketCreationRejectsUnknownCatalogFieldsAndIllegalDictionaryOptions() throws Exception {
        mockMvc.perform(create("""
            {"serviceCatalogItemId":"SC-browser-performance","serviceCatalogFormVersion":1,"type":"INCIDENT","title":"页面很慢","description":"描述足够长",
             "structuredFields":{"browser":"Safari","serverPriority":"P1"},"tags":[]}
            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("SERVICE_CATALOG_INVALID")));

        mockMvc.perform(create("""
            {"serviceCatalogItemId":"unpublished-or-unknown","serviceCatalogFormVersion":1,"type":"INCIDENT","title":"页面很慢","description":"描述足够长",
             "structuredFields":{},"tags":[]}
            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("SERVICE_CATALOG_INVALID")));
    }

    @Test
    void draftMatchReturnsSuggestionsButDoesNotCreateATicket() throws Exception {
        mockMvc.perform(post("/api/v1/service-catalog/rule-matches")
                .with(user("iam-u-1001").roles("REQUESTER")).with(csrf())
                .header("Idempotency-Key", "123e4567-e89b-42d3-a456-426614174000")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"serviceCatalogItemId":"SC-browser-performance","formVersion":1,"structuredFields":{"browser":"Chrome"},
                     "tags":[{"name":"#页面卡顿","kind":"STANDARD"}],"title":"核协 E+ 卡顿"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ruleEngine", is("DETERMINISTIC_RULES")))
            .andExpect(jsonPath("$.matches", hasSize(1)))
            .andExpect(jsonPath("$.matches[0].suggestion.referenceId", is("case-browser-cache")));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create(String content) {
        return post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf())
            .header("Idempotency-Key", "8f3d9c4b-1234-4abc-8def-123456789012")
            .contentType(MediaType.APPLICATION_JSON).content(content);
    }
}
