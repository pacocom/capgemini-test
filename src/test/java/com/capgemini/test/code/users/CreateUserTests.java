package com.capgemini.test.code.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(statements = "DELETE FROM users WHERE email LIKE '%@step6.test'",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CreateUserTests {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;

  @Test
  void createsAdminAndSendsEmail() throws Exception {
    var request = request();
    String body = mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request)))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
        .andReturn().getResponse().getContentAsString();
    int id = json.readTree(body).get("id").asInt();
    mvc.perform(get("/users/" + id))
        .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("pablo"))
        .andExpect(jsonPath("$.rol").value("admin")).andExpect(jsonPath("$.roomId").value(1));
    assertExternalRequest("PATCH", "/check-dni", Map.of("dni", request.get("dni")));
    assertExternalRequest("POST", "/email", Map.of("email", request.get("email"), "message", "usuario guardado"));
  }

  @Test
  void createsSuperadminAndSendsSms() throws Exception {
    var request = request();
    request.put("rol", "superadmin");
    mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request)))
        .andExpect(status().isCreated());
    assertExternalRequest("POST", "/sms", Map.of("phone", request.get("phone"), "message", "usuario guardado"));
  }

  static Stream<Arguments> invalidFields() {
    return Stream.of(
        Arguments.of("name", "toolong", "userName"),
        Arguments.of("name", "", "userName"),
        Arguments.of("email", "missing-at.test", "email"),
        Arguments.of("email", "missing@dot", "email"),
        Arguments.of("rol", "USER", "rol"),
        Arguments.of("rol", "ADMIN", "rol"),
        Arguments.of("dni", "", "dni"),
        Arguments.of("phone", "", "phone"),
        Arguments.of("email", "juan@example.com", "email"),
        Arguments.of("dni", "12345678Z", "dni"),
        Arguments.of("phone", "600000001", "phone"),
        Arguments.of("dni", "99999999w", "dni"));
  }

  @ParameterizedTest
  @MethodSource("invalidFields")
  void rejectsInvalidUsersWithoutSaving(String field, String value, String errorField) throws Exception {
    long before = users.count();
    var request = request();
    request.put(field, value);
    mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request)))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409))
        .andExpect(jsonPath("$.message").value("error validation " + errorField));
    assertThat(users.count()).isEqualTo(before);
  }

  @Test
  void acceptsSixCharacterName() throws Exception {
    var request = request();
    request.put("name", "carlos");
    mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  void rejectsMissingFields() throws Exception {
    mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
  }

  private Map<String, String> request() {
    return new java.util.HashMap<>(Map.of("name", "pablo", "email", "pablo@step6.test",
        "phone", "677998899", "rol", "admin", "dni", "23454234W"));
  }

  private void assertExternalRequest(String method, String path, Map<String, String> body) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String matcher = json.writeValueAsString(Map.of("method", method, "path", path,
        "body", Map.of("type", "JSON", "json", json.writeValueAsString(body))));
    String result = new RestTemplate().exchange(
        "http://localhost:1080/mockserver/retrieve?type=REQUESTS", HttpMethod.PUT,
        new HttpEntity<>(matcher, headers), String.class).getBody();
    JsonNode requests = json.readTree(result);
    assertThat(requests.isArray()).isTrue();
    assertThat(requests.size()).isGreaterThan(0);
  }
}
