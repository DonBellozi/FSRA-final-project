package ru.mephi.fsra.hotel;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import ru.mephi.fsra.hotel.controller.HotelController;
import ru.mephi.fsra.hotel.entity.Hotel;
import ru.mephi.fsra.hotel.service.HotelService;

class HotelCrudControllerTest {
  @Test
  void hotelIdIsBoundForReadUpdateAndDelete() throws Exception {
    HotelService service = mock(HotelService.class);
    Hotel hotel = new Hotel();
    hotel.setId(42L);
    hotel.setName("Updated");
    when(service.getHotelById(42L)).thenReturn(hotel);
    when(service.updateHotel(eq(42L), any())).thenReturn(hotel);
    var mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new HotelController(service))
            .build();
    mvc.perform(get("/api/hotels/42"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(42));
    mvc.perform(
            put("/api/hotels/42")
                .contentType("application/json")
                .content("{\"name\":\"Updated\",\"address\":\"Address\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated"));
    mvc.perform(delete("/api/hotels/42")).andExpect(status().isNoContent());
    verify(service).deleteHotel(42L);
  }
}
