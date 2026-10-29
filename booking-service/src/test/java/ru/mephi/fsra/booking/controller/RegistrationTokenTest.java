package ru.mephi.fsra.booking.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:registration-token-test"
    })
@AutoConfigureMockMvc
class RegistrationTokenTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JwtDecoder decoder;

  @Test
  void registrationAndLoginIssueUsableOneHourTokens() throws Exception {
    String body = "{\"username\":\"token-test-user\",\"password\":\"Student123!\"}";
    var response =
        mvc.perform(post("/api/users/register").contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(3600))
            .andReturn();
    var json = mapper.readTree(response.getResponse().getContentAsString());
    String token = json.get("accessToken").asText();
    var jwt = decoder.decode(token);
    assertEquals("USER", jwt.getClaimAsString("role"));
    assertEquals(
        3600, java.time.Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds());
    mvc.perform(
            get("/api/bookings/user/" + json.get("id").asLong())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(post("/api/users/auth").contentType("application/json").content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());
    mvc.perform(
            post("/api/users/auth")
                .contentType("application/json")
                .content("{\"username\":\"token-test-user\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized());
  }
}
