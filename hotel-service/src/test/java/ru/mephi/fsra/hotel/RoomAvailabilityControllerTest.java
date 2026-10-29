package ru.mephi.fsra.hotel;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.mephi.fsra.hotel.service.RoomLockService;

@WebMvcTest(ru.mephi.fsra.hotel.controller.RoomAvailabilityController.class)
@Import(ru.mephi.fsra.hotel.config.SecurityConfig.class)
class RoomAvailabilityControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean RoomLockService locks;
  @MockitoBean JwtDecoder decoder;

  @Test
  void internalCanConfirmAndReleaseButUserCannot() throws Exception {
    var internal =
        jwt()
            .authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "ROLE_INTERNAL"));
    mvc.perform(
            post("/api/rooms/7/confirm-availability")
                .with(internal)
                .contentType("application/json")
                .content(
                    "{\"bookingId\":42,\"checkIn\":\"2027-01-01\",\"checkOut\":\"2027-01-02\"}"))
        .andExpect(status().isCreated());
    verify(locks)
        .lockRoom(7L, 42L, java.time.LocalDate.of(2027, 1, 1), java.time.LocalDate.of(2027, 1, 2));
    mvc.perform(
            post("/api/rooms/7/release")
                .with(internal)
                .contentType("application/json")
                .content("{\"bookingId\":42}"))
        .andExpect(status().isNoContent());
    verify(locks).unlockRoom(7L, 42L);
    mvc.perform(
            post("/api/rooms/7/release")
                .with(
                    jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_USER")))
                .contentType("application/json")
                .content("{\"bookingId\":42}"))
        .andExpect(status().isForbidden());
    verifyNoMoreInteractions(locks);
  }
}
