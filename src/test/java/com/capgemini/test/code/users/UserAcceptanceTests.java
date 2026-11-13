package com.capgemini.test.code.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(statements = "DELETE FROM users WHERE email LIKE '%@acceptance.test'",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class UserAcceptanceTests {

  @Autowired private TestRestTemplate http;
  @Autowired private ObjectMapper json;
  @Value("${external.service.url}") private String externalUrl;

  @ParameterizedTest
  @ValueSource(strings = {"admin", "superadmin"})
  void createsRetrievesAndNotifiesOverRealHttp(String role) throws Exception {
    Map<String, String> request = request(role);
    var created = http.postForEntity("/users", request, JsonNode.class);
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    assertThat(created.getBody().path("id").isIntegralNumber()).isTrue();
    int id = created.getBody().path("id").asInt();
    assertThat(created.getHeaders().getLocation()).hasPath("/users/" + id);

    var retrieved = http.getForEntity("/users/" + id, JsonNode.class);
    assertThat(retrieved.getStatusCode().value()).isEqualTo(200);
    for (var field : request.entrySet()) {
      assertThat(retrieved.getBody().path(field.getKey()).asText()).isEqualTo(field.getValue());
    }
    assertThat(retrieved.getBody().path("roomId").asLong()).isEqualTo(1L);

    assertThat(recorded("PATCH", "/check-dni", Map.of("dni", request.get("dni"))).size())
        .isEqualTo(1);
    String recipientField = "admin".equals(role) ? "email" : "phone";
    String path = "admin".equals(role) ? "/email" : "/sms";
    assertThat(recorded("POST", path, Map.of(recipientField, request.get(recipientField),
        "message", "usuario guardado")).size()).isEqualTo(1);

    String otherPath = "admin".equals(role) ? "/sms" : "/email";
    String otherRecipient = "admin".equals(role) ? "phone" : "email";
    assertThat(recorded("POST", otherPath,
        Map.of(otherRecipient, request.get(otherRecipient))).size()).isZero();
  }

  @Test
  void rejectsDuplicateEmailOverRealHttp() {
    Map<String, String> request = request("admin");
    request.put("email", "juan@example.com");
    assertConflict(request, "email");
  }

  @Test
  void rejectsLongNameOverRealHttp() {
    Map<String, String> request = request("admin");
    request.put("name", "toolong");
    assertConflict(request, "userName");
  }

  @Test
  void rejectsInvalidDniWithoutNotifyingOverRealHttp() throws Exception {
    Map<String, String> request = request("admin");
    request.put("email", "invalid-dni@acceptance.test");
    request.put("dni", "99999999w");
    assertConflict(request, "dni");
    assertThat(recorded("POST", "/email", Map.of("email", request.get("email"))).size())
        .isZero();
  }

  private void assertConflict(Map<String, String> request, String field) {
    var response = http.postForEntity("/users", request, JsonNode.class);
    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody().path("code").asInt()).isEqualTo(409);
    assertThat(response.getBody().path("message").asText()).isEqualTo("error validation " + field);
  }

  private Map<String, String> request(String role) {
    return new HashMap<>(Map.of("name", "pablo", "email", role + "@acceptance.test",
        "phone", "admin".equals(role) ? "611111111" : "611111112",
        "rol", role, "dni", "admin".equals(role) ? "22222222A" : "22222222B"));
  }

  private JsonNode recorded(String method, String path, Map<String, String> body) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String matcher = json.writeValueAsString(Map.of("method", method, "path", path,
        "body", Map.of("type", "JSON", "matchType", "ONLY_MATCHING_FIELDS",
            "json", json.writeValueAsString(body))));
    var response = http.exchange(externalUrl + "/mockserver/retrieve?type=REQUESTS",
        HttpMethod.PUT, new HttpEntity<>(matcher, headers), JsonNode.class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isArray()).isTrue();
    return response.getBody();
  }
}
