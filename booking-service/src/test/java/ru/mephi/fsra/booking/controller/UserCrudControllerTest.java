package ru.mephi.fsra.booking.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import ru.mephi.fsra.booking.entity.User;
import ru.mephi.fsra.booking.service.UserService;

class UserCrudControllerTest {
  @Test
  void userIdIsBoundForUpdateAndDelete() throws Exception {
    UserService service = mock(UserService.class);
    User user = new User();
    user.setId(42L);
    user.setUsername("updated");
    when(service.update(eq(42L), eq("updated"), isNull(), isNull())).thenReturn(user);
    var mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new UserController(service, mock(ru.mephi.fsra.booking.service.TokenService.class)))
            .build();
    mvc.perform(
            patch("/api/users/42")
                .contentType("application/json")
                .content("{\"username\":\"updated\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(42));
    mvc.perform(delete("/api/users/42")).andExpect(status().isNoContent());
    verify(service).delete(42L);
  }
}
