package com.capgemini.test.code.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capgemini.test.code.clients.DniClient;
import com.capgemini.test.code.clients.NotificationClient;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CreateUserExternalFailureTests {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @MockBean private NotificationClient notifications;
  @MockBean private DniClient dni;

  @Test
  void rollsBackUserWhenNotificationFails() throws Exception {
    long before = users.count();
    doThrow(unavailable()).when(notifications).email(any());
    createExpectingConflict("email");
    assertThat(users.count()).isEqualTo(before);
    assertThat(users.existsByEmail("failure@step6.test")).isFalse();
  }

  @Test
  void doesNotSaveWhenDniServiceFails() throws Exception {
    long before = users.count();
    when(dni.check(any())).thenThrow(unavailable());
    createExpectingConflict("dni");
    assertThat(users.count()).isEqualTo(before);
  }

  private void createExpectingConflict(String field) throws Exception {
    mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("""
        {"name":"pablo","email":"failure@step6.test","phone":"677998899",
         "rol":"admin","dni":"23454234W"}
        """))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409))
        .andExpect(jsonPath("$.message").value("error validation " + field));
  }

  private FeignException unavailable() {
    Request request = Request.create(Request.HttpMethod.POST, "http://localhost/unavailable",
        Map.of(), null, java.nio.charset.StandardCharsets.UTF_8, null);
    return FeignException.errorStatus("external", Response.builder()
        .request(request).status(503).reason("Unavailable").headers(Map.of()).build());
  }
}
