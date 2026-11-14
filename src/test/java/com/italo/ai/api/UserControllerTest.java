package com.italo.ai.api;

import com.italo.ai.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService users;

    @Test void createsUserWithOptionalTelegram() throws Exception {
        var user = new UserEntity("italo", 5123456789L);
        when(users.create("italo", 5123456789L, "SenhaTeste123!")).thenReturn(user);
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"italo\",\"telegramUserId\":5123456789,\"password\":\"SenhaTeste123!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.username").value("italo"))
                .andExpect(jsonPath("$.telegramUserId").value(5123456789L));
    }

    @Test void linksTelegramByUsername() throws Exception {
        when(users.linkTelegram("italo", 5123456789L, "SenhaTeste123!"))
                .thenReturn(java.util.Optional.of(new UserEntity("italo", 5123456789L)));
        mvc.perform(put("/api/v1/users/italo/telegram").contentType(MediaType.APPLICATION_JSON)
                .content("{\"telegramUserId\":5123456789,\"password\":\"SenhaTeste123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramUserId").value(5123456789L));
    }

    @Test void mapsDuplicateRegistrationToConflict() throws Exception {
        when(users.create("italo", null, "SenhaTeste123!")).thenThrow(new DataIntegrityViolationException("SQL privado"));
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"italo\",\"password\":\"SenhaTeste123!\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O nome de usuário ou o ID do Telegram já está cadastrado."));
    }

    @Test void mapsInvalidInputToBadRequest() throws Exception {
        when(users.create(" ", null, null)).thenThrow(new IllegalArgumentException("Informe o nome de usuário."));
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void mapsUnknownUserToUnauthorized() throws Exception {
        when(users.linkTelegram("ausente", 1L, null)).thenReturn(java.util.Optional.empty());
        mvc.perform(put("/api/v1/users/ausente/telegram").contentType(MediaType.APPLICATION_JSON)
                .content("{\"telegramUserId\":1}"))
                .andExpect(status().isUnauthorized());
    }
}
