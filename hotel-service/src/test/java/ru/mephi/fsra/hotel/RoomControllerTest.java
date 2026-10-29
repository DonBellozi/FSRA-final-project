package ru.mephi.fsra.hotel;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.mephi.fsra.hotel.controller.RoomController;
import ru.mephi.fsra.hotel.service.RoomService;

@WebMvcTest(RoomController.class)
@org.springframework.context.annotation.Import(ru.mephi.fsra.hotel.config.SecurityConfig.class)
class RoomControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean RoomService rooms;
  @MockitoBean ru.mephi.fsra.hotel.service.RoomStatisticsService statistics;
  @MockitoBean JwtDecoder decoder;

  @Test
  void errorDispatchIsNotReplacedWithAccessDenied() throws Exception {
    mvc.perform(
            get("/error")
                .with(
                    request -> {
                      request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
                      request.setAttribute(
                          jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 409);
                      return request;
                    }))
        .andExpect(status().isConflict());
  }

  @Test
  void invalidDateHasConsistentErrorBody() throws Exception {
    mvc.perform(
            get("/api/rooms/recommend")
                .param("checkIn", "wrong")
                .param("checkOut", "2027-02-12")
                .with(
                    jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_INTERNAL"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.path").value("/api/rooms/recommend"))
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void createRoomAtSpecificationPathBindsHotelIdFromBody() throws Exception {
    var result = new ru.mephi.fsra.hotel.entity.Room();
    result.setId(20L);
    result.setNumber("101");
    when(rooms.createRoom(eq(7L), any())).thenReturn(result);
    mvc.perform(
            post("/api/rooms")
                .contentType("application/json")
                .content("{\"hotelId\":7,\"number\":\"101\"}")
                .with(
                    jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_ADMIN"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(20));
    verify(rooms)
        .createRoom(eq(7L), argThat(room -> room.isAvailable() && room.getNumber().equals("101")));
  }

  @Test
  void roomListExcludesOperationallyUnavailableRooms() throws Exception {
    mvc.perform(
            get("/api/rooms")
                .with(
                    jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_USER"))))
        .andExpect(status().isOk());
    verify(rooms).getAvailableRooms();
    verify(rooms, never()).getAllRooms();
  }

  @Test
  void isoDatesReachRecommendationService() throws Exception {
    mvc.perform(
            get("/api/rooms/recommend")
                .param("checkIn", "2027-02-10")
                .param("checkOut", "2027-02-12")
                .with(
                    jwt()
                        .jwt(token -> token.claim("role", "INTERNAL"))
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_INTERNAL"))))
        .andExpect(status().isOk());
    verify(rooms).recommendRooms(LocalDate.of(2027, 2, 10), LocalDate.of(2027, 2, 12));
  }
}
