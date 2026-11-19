package com.italo.ai.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.italo.ai.chat.ChatService;
import com.italo.ai.finance.FinanceLinks.LinkResult;
import com.italo.ai.memory.indexing.AsyncMemoryIndexingService;
import com.italo.ai.memory.service.MemoryContextService;
import com.italo.ai.memory.service.MemoryExtractor;
import com.italo.ai.memory.service.MemoryService;
import com.italo.ai.tools.TimeTool;
import com.italo.ai.finance.FinanceModels.AccountDto;
import com.italo.ai.finance.FinanceModels.AccountType;
import com.italo.ai.finance.FinanceOAuth.InvalidAuthorization;
import com.italo.ai.finance.FinanceOAuth.Tokens;
import com.italo.ai.user.UserEntity;
import com.italo.ai.user.UserRepository;
import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:finance-links;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FinanceLinksTest.LinkTestConfiguration.class)
class FinanceLinksTest {
    private static final URI KEYCLOAK = URI.create("https://keycloak.example/auth");
    @Autowired FinanceLinks links;
    @Autowired FinanceOAuth oauth;
    @Autowired UserRepository users;
    @Autowired JdbcClient database;

    @Test
    void bindsCallbackToBrowserAndConsumesTheLinkOnce() {
        var user = users.saveAndFlush(new UserEntity("link-user", 101L));
        String ticket = ticket(links.createLink(user.getId()));
        when(oauth.authorizationUri(eq(ticket), anyString())).thenReturn(KEYCLOAK);
        var browser = links.begin(ticket).orElseThrow();
        assertThat(browser.uri()).isEqualTo(KEYCLOAK);
        assertThat(links.begin(ticket)).isEmpty();

        assertThat(links.complete(ticket, null, "code", null)).isEqualTo(LinkResult.INVALID);
        assertThat(links.accessToken(user.getId(), null)).isEmpty();

        when(oauth.exchange(eq("code"), anyString()))
                .thenReturn(new Tokens("subject", "access", "refresh", Instant.now().plusSeconds(120)));
        assertThat(links.complete(ticket, browser.browserSecret(), "code", null)).isEqualTo(LinkResult.LINKED);
        assertThat(links.complete(ticket, browser.browserSecret(), "code", null)).isEqualTo(LinkResult.INVALID);
        assertThat(links.accessToken(user.getId(), null)).contains("access");
        assertThat(database.sql("SELECT refresh_token FROM finance_link WHERE user_id = ?")
                .param(user.getId()).query(String.class).single()).isNotEqualTo("refresh");
    }

    @Test
    void refreshesOnlyTheOwningUserAndRetainsTheRotatedCredential() {
        var alice = users.saveAndFlush(new UserEntity("finance-alice", 102L));
        var bob = users.saveAndFlush(new UserEntity("finance-bob", 103L));
        link(alice, new Tokens("alice", "old-a", "refresh-a", Instant.now().plusSeconds(5)));
        link(bob, new Tokens("bob", "access-b", "refresh-b", Instant.now().plusSeconds(120)));
        when(oauth.refresh("alice", "refresh-a"))
                .thenReturn(new Tokens("alice", "access-a", "rotated-a", Instant.now().plusSeconds(120)));
        when(oauth.refresh("alice", "rotated-a")).thenThrow(new InvalidAuthorization());

        var client = mock(FinanceClient.class);
        when(client.listAccounts("Bearer access-a")).thenReturn(ResponseEntity.ok(List.of(
                new AccountDto("Conta Alice", AccountType.CHECKING_ACCOUNT, alice.getId(), true, null))));
        when(client.listAccounts("Bearer access-b")).thenReturn(ResponseEntity.ok(List.of(
                new AccountDto("Conta Bob", AccountType.CHECKING_ACCOUNT, bob.getId(), true, null))));
        var telegram = new FinanceTelegram(links, client);

        assertThat(telegram.toolsFor(alice).consultarContas(null).dados())
                .extracting(AccountDto::name).containsExactly("Conta Alice");
        assertThat(telegram.toolsFor(bob).consultarContas(null).dados())
                .extracting(AccountDto::name).containsExactly("Conta Bob");
        assertThat(links.accessToken(alice.getId(), "old-a")).contains("access-a");
        assertThat(links.accessToken(alice.getId(), "access-a")).isEmpty();
        assertThat(links.accessToken(bob.getId(), null)).contains("access-b");
    }

    @Test
    void chatExposesFinanceToolsAndAnUnauthorizedCallDoesNotReachFinance() {
        var user = users.saveAndFlush(new UserEntity("finance-chat", 105L));
        var finance = mock(FinanceClient.class);
        var tools = new FinanceTelegram(links, finance).toolsFor(user);
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            var options = (ToolCallingChatOptions) prompt.getOptions();
            assertThat(options.getToolCallbacks()).extracting(tool -> tool.getToolDefinition().name())
                    .contains("getLocalDateTime", "consultarContas", "consultarFatura");
            var callback = options.getToolCallbacks().stream()
                    .filter(tool -> "consultarContas".equals(tool.getToolDefinition().name())).findFirst().orElseThrow();
            assertThat(callback.call("""
                    {"nome":"Conta corrente"}
                    """)).contains("autorização do usuário").doesNotContain("http");
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Autorize o Finance."))));
        });
        var extractor = mock(MemoryExtractor.class);
        var context = mock(MemoryContextService.class);
        String question = "Qual o saldo da minha conta corrente?";
        when(context.contextFor(user.getId(), question)).thenReturn("");
        var chat = new ChatService(ChatClient.builder(model).defaultTools(new TimeTool()).build(),
                extractor, mock(MemoryService.class), context, mock(AsyncMemoryIndexingService.class));

        chat.chat(user, question, tools);

        assertThat(tools.needsLogin()).isTrue();
        verifyNoInteractions(finance, extractor);
    }

    @Test
    void rejectsExpiredTicketBeforeRedirecting() {
        var user = users.saveAndFlush(new UserEntity("expired-link", 104L));
        String ticket = ticket(links.createLink(user.getId()));
        database.sql("UPDATE finance_link_request SET expires_at = CURRENT_TIMESTAMP WHERE user_id = ?")
                .param(user.getId()).update();

        assertThat(links.begin(ticket)).isEmpty();
    }

    private void link(UserEntity user, Tokens tokens) {
        String ticket = ticket(links.createLink(user.getId()));
        String code = "code-" + tokens.subject();
        when(oauth.authorizationUri(eq(ticket), anyString())).thenReturn(KEYCLOAK);
        when(oauth.exchange(eq(code), anyString())).thenReturn(tokens);
        assertThat(links.complete(ticket, links.begin(ticket).orElseThrow().browserSecret(), code, null))
                .isEqualTo(LinkResult.LINKED);
    }

    private String ticket(URI uri) {
        return uri.getQuery().substring("ticket=".length());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LinkTestConfiguration {
        @Bean
        FinanceOAuth oauth() {
            return mock(FinanceOAuth.class);
        }

        @Bean
        FinanceLinks links(FinanceOAuth oauth, JdbcClient database) {
            String key = Base64.getEncoder().encodeToString(new byte[32]);
            var settings = new FinanceOAuthSettings("https://keycloak.example/realms/finance", "ai-finance", "test",
                    URI.create("http://localhost:8080/login/oauth2/code/finance"), key);
            return new FinanceLinks(settings, oauth, database, new FinanceTokenEncryption(key));
        }
    }
}
