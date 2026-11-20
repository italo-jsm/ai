package com.italo.ai.api;

import com.italo.ai.chat.ChatService;
import com.italo.ai.user.UserService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.italo.ai.support.TestUsers.ALICE;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatController.class, properties = "app.chat.require-authentication=true")
class AuthenticatedChatControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatService chatService;

    @MockitoBean
    UserService users;

    @Test
    void anonymousChatIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"alice","message":"olá"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));

        verifyNoInteractions(users, chatService);
    }

    @Test
    void chatUsesAuthenticatedAccountEvenWithDifferentUsernameInBody() throws Exception {
        when(users.authenticate("alice", "test-password")).thenReturn(Optional.of(ALICE));
        when(chatService.chat(ALICE, "olá")).thenReturn("Oi!");
        var credentials = Base64.getEncoder().encodeToString(
                "alice:test-password".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(post("/api/v1/chat")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"bob","message":"olá"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Oi!"));
    }
}
