package com.capgemini.test.code.users;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void returnsUserFromRoomOne() throws Exception {
    mockMvc.perform(get("/users/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.name").value("Juan"))
        .andExpect(jsonPath("$.email").value("juan@example.com"))
        .andExpect(jsonPath("$.phone").value("600000001"))
        .andExpect(jsonPath("$.rol").value("admin"))
        .andExpect(jsonPath("$.dni").value("12345678Z"))
        .andExpect(jsonPath("$.roomId").value(1));
  }

  @Test
  void doesNotReturnUserFromAnotherRoom() throws Exception {
    mockMvc.perform(get("/users/2")).andExpect(status().isNotFound());
  }

  @Test
  void returnsNotFoundForMissingUser() throws Exception {
    mockMvc.perform(get("/users/999999")).andExpect(status().isNotFound());
  }

  @Test
  void rejectsNonNumericId() throws Exception {
    mockMvc.perform(get("/users/abc")).andExpect(status().isBadRequest());
  }
}
