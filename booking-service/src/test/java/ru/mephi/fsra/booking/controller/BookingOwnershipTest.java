package ru.mephi.fsra.booking.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.mephi.fsra.booking.client.HotelClient;
import ru.mephi.fsra.booking.entity.*;
import ru.mephi.fsra.booking.repository.BookingRepository;
import ru.mephi.fsra.booking.service.*;

@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:ownership-test;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
class BookingOwnershipTest {
  @Autowired MockMvc mvc;
  @Autowired BookingController controller;
  @Autowired UserService users;
  @Autowired TokenService tokens;
  @Autowired BookingRepository bookings;
  @MockitoBean HotelClient hotel;

  private Booking fixture(Long ownerId, Long roomId) {
    Booking booking = new Booking();
    booking.setUserId(ownerId);
    booking.setRoomId(roomId);
    booking.setCheckIn(LocalDate.now().plusDays(10));
    booking.setCheckOut(LocalDate.now().plusDays(12));
    booking.setStatus(BookingStatus.CONFIRMED);
    return bookings.saveAndFlush(booking);
  }

  @Test
  @org.springframework.security.test.context.support.WithMockUser(roles = "USER")
  void methodSecurityRejectsDirectAdministrativeCall() {
    assertThrows(
        org.springframework.security.access.AccessDeniedException.class,
        () -> controller.getAllBookings(null, null));
  }

  @Test
  void personalOperationsAreRestrictedToOwnerOrAdmin() throws Exception {
    User owner = users.register("booking-owner", "Student123!");
    User other = users.register("booking-other", "Student123!");
    String ownerToken = "Bearer " + tokens.issue(owner).accessToken();
    String otherToken = "Bearer " + tokens.issue(other).accessToken();
    String adminToken =
        "Bearer " + tokens.issue(users.authenticate("admin", "StudyAdmin123!")).accessToken();
    Booking first = fixture(owner.getId(), 10L);
    Booking second = fixture(owner.getId(), 11L);
    mvc.perform(get("/api/bookings/user/" + owner.getId()).header("Authorization", otherToken))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/bookings/" + first.getId()).header("Authorization", otherToken))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/bookings/" + first.getId() + "/cancel").header("Authorization", otherToken))
        .andExpect(status().isForbidden());
    verifyNoInteractions(hotel);
    assertEquals(
        BookingStatus.CONFIRMED, bookings.findById(first.getId()).orElseThrow().getStatus());
    mvc.perform(get("/api/bookings").header("Authorization", otherToken))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/bookings/user/" + other.getId()).header("Authorization", otherToken))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
    mvc.perform(get("/api/bookings/user/" + owner.getId()).header("Authorization", ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
    mvc.perform(
            get("/api/bookings/user/" + owner.getId())
                .param("page", "0")
                .param("size", "1")
                .header("Authorization", ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2));
    mvc.perform(
            get("/api/bookings/user/" + owner.getId())
                .param("page", "1")
                .param("size", "1")
                .header("Authorization", ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(first.getId()));
    mvc.perform(
            get("/api/bookings/user/" + owner.getId())
                .param("size", "0")
                .header("Authorization", ownerToken))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/bookings/user/" + owner.getId())
                .param("page", "0")
                .header("Authorization", otherToken))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/bookings/" + first.getId()).header("Authorization", ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(owner.getId()));
    mvc.perform(
            patch("/api/bookings/" + first.getId() + "/cancel").header("Authorization", ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    verify(hotel).unlockRoom(first.getRoomId(), first.getId());
    mvc.perform(get("/api/bookings/user/" + owner.getId()).header("Authorization", adminToken))
        .andExpect(status().isOk());
    mvc.perform(
            patch("/api/bookings/" + second.getId() + "/cancel")
                .header("Authorization", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    verify(hotel).unlockRoom(second.getRoomId(), second.getId());
  }
}
