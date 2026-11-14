package com.italo.ai.api;

import com.italo.ai.chat.ChatService;
import com.italo.ai.user.UserService;
import com.italo.ai.user.UserNotFoundException;
import static com.italo.ai.support.TestUsers.ALICE;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatService chatService;

    @MockitoBean
    UserService users;

    @Test
    void chatShouldDelegateToServiceAndReturnAnswer() throws Exception {
        when(users.findByUsername("alice")).thenReturn(ALICE);
        when(chatService.chat(ALICE, "olá")).thenReturn("Oi!");

        mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"alice","message":"olá"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Oi!"));

        verify(chatService).chat(ALICE, "olá");
    }
    @Test
    void unknownUserReturnsNotFoundWithoutCallingChat() throws Exception {
        when(users.findByUsername("unknown")).thenThrow(new UserNotFoundException());
        mockMvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"unknown\",\"message\":\"olá\"}"))
                .andExpect(status().isNotFound());
        org.mockito.Mockito.verifyNoInteractions(chatService);
    }
}
