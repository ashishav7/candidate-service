package com.candidatehub.candidate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through HTTP -> controller -> service -> Hibernate -> H2. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CandidateApiIntegrationTest {

    private static final String BASE = "/api/v1/candidates";

    @Autowired
    MockMvc mvc;
    @Autowired
    CandidateRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private static String json(String email) {
        return """
                {"firstName":"Jane","lastName":"Doe","email":"%s","phone":"+91 98765 43210",
                 "currentTitle":"Engineer","currentCompany":"Acme","location":"Gurugram",
                 "yearsOfExperience":5,"skills":["Java","Kafka"]}
                """.formatted(email);
    }

    private long createCandidate(String email) throws Exception {
        MvcResult result = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String idPart = body.substring(body.indexOf("\"id\":") + 5);
        return Long.parseLong(idPart.substring(0, idPart.indexOf(',')).trim());
    }

    @Test
    void createReturns201WithLocationAndNormalisedData() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json("Jane@Acme.COM")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost" + BASE + "/")))
                .andExpect(jsonPath("$.email").value("jane@acme.com"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.skills", containsInAnyOrder("java", "kafka")))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void fullCrudLifecycle() throws Exception {
        long id = createCandidate("jane@acme.com");

        mvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"));

        String updated = """
                {"firstName":"Janet","lastName":"Doe","email":"jane@acme.com","yearsOfExperience":6,
                 "status":"SCREENING","skills":["java"],"version":0}
                """;
        mvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Janet"))
                .andExpect(jsonPath("$.status").value("SCREENING"))
                .andExpect(jsonPath("$.skills", hasSize(1)))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        String bad = """
                {"firstName":"","lastName":"Doe","email":"not-an-email","yearsOfExperience":-3,"phone":"abc"}
                """;
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.yearsOfExperience").exists())
                .andExpect(jsonPath("$.errors.phone").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownEnumValueReturns400() throws Exception {
        String bad = """
                {"firstName":"A","lastName":"B","email":"a@b.com","yearsOfExperience":1,"status":"AWESOME"}
                """;
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        createCandidate("jane@acme.com");
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json("JANE@acme.com")))
                .andExpect(status().isConflict());
    }

    @Test
    void staleVersionOnUpdateReturns409() throws Exception {
        long id = createCandidate("jane@acme.com");
        String stale = """
                {"firstName":"Jane","lastName":"Doe","email":"jane@acme.com","yearsOfExperience":5,"version":42}
                """;
        mvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(stale))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownIdReturns404ForReadUpdateDelete() throws Exception {
        mvc.perform(get(BASE + "/999999")).andExpect(status().isNotFound());
        mvc.perform(put(BASE + "/999999").contentType(MediaType.APPLICATION_JSON).content(json("x@y.com")))
                .andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/999999")).andExpect(status().isNotFound());
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mvc.perform(get(BASE + "/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void searchAndFilterOverHttp() throws Exception {
        createCandidate("jane@acme.com");
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"John","lastName":"Smith","email":"john@globex.com","yearsOfExperience":1,
                 "location":"Pune","status":"REJECTED","skills":["angular"]}"""))
                .andExpect(status().isCreated());

        mvc.perform(get(BASE).param("q", "jane").param("skills", "java").param("status", "NEW")
                        .param("minExperience", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Jane"));

        mvc.perform(get(BASE).param("location", "pune"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].firstName").value("John"));
    }

    @Test
    void invalidSearchParametersReturn400() throws Exception {
        mvc.perform(get(BASE).param("status", "NOPE")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("minExperience", "9").param("maxExperience", "2"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("sort", "password,asc")).andExpect(status().isBadRequest());
    }

    @Test
    void pageSizeIsCapped() throws Exception {
        mvc.perform(get(BASE).param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void responsesCarryRequestId() throws Exception {
        mvc.perform(get(BASE).header("X-Request-Id", "abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"));
        mvc.perform(get(BASE).header("X-Request-Id", "bad id\r\nInjected: 1"))
                .andExpect(header().exists("X-Request-Id"));
    }
}
