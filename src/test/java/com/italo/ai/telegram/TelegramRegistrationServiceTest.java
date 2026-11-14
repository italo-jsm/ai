package com.italo.ai.telegram;

import com.italo.ai.telegram.model.TelegramUpdate;
import com.italo.ai.user.UserRepository;
import com.italo.ai.user.UserEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.MailSendException;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:registration;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TelegramRegistrationService.class, com.italo.ai.user.UserService.class})
class TelegramRegistrationServiceTest {
    @Autowired TelegramRegistrationService service;
    @Autowired TelegramRegistrationRepository registrations;
    @Autowired UserRepository users;
    @Autowired com.italo.ai.user.UserService userService;
    @Autowired EntityManager entityManager;
    @MockitoBean RegistrationEmailSender mail;
    private long sequence = 1;

    String send(String text) { return service.handle(update(text, "private", sequence++)); }
    TelegramUpdate update(String text, String type, long updateId) {
        return new TelegramUpdate(updateId, new TelegramUpdate.Message(updateId,
                new TelegramUpdate.User(123L, false, null, null, null, null),
                new TelegramUpdate.Chat(123L, type, null, null, null, null),
                null, text, null), null, null, null);
    }
    String reachCode() {
        send("/start"); send("Italo Silva"); send("ItAlO"); send("SenhaTeste123!"); send("ITALO@example.com");
        var code = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq("italo@example.com"), code.capture());
        return code.getValue();
    }

    @Test void completesRegistrationAtomicallyAndRepeatedConfirmationDoesNotDuplicateUser() {
        String code = reachCode();
        assertThat(code).matches("[0-9]{6}");
        assertThat(users.count()).isZero();
        assertThat(registrations.findById(123L).orElseThrow().codeHash).doesNotContain(code);
        assertThat(send(code)).contains("Cadastro concluído");
        var user = users.findByTelegramUserId(123L).orElseThrow();
        assertThat(user.getFullName()).isEqualTo("Italo Silva");
        assertThat(user.getUsername()).isEqualTo("italo");
        assertThat(user.getEmail()).isEqualTo("italo@example.com");
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(registrations.count()).isZero();
        send(code);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test void resumesPersistedProgressAndDoesNotConsumeRepeatedUpdatesTwice() {
        send("/start"); send("Italo Silva");
        entityManager.flush(); entityManager.clear();
        assertThat(send("/start")).contains("nome de usuário");
        var update = update("italo", "private", sequence++);
        assertThat(service.handle(update)).contains("Crie uma senha");
        assertThat(service.handle(update)).contains("Crie uma senha");
        assertThat(registrations.findById(123L).orElseThrow().email).isNull();
        send("/cancelar");
        assertThat(registrations.count()).isZero();
        assertThat(send("/start")).contains("nome completo");
    }

    @Test void validatesFieldsAndRejectsTakenUsernameAndEmail() {
        users.saveAndFlush(new UserEntity("ocupado", 456L, "Outro", "outro@example.com"));
        send("/start");
        assertThat(send("x")).contains("nome completo");
        send("Italo Silva");
        assertThat(send("ab")).contains("3 a 32");
        assertThat(send("OCUPADO")).contains("já está em uso");
        send("italo"); send("SenhaTeste123!");
        assertThat(send("invalido")).contains("e-mail válido");
        assertThat(send("OUTRO@example.com")).contains("já está em uso");
        verifyNoInteractions(mail);
    }

    @Test void limitsAttemptsRejectsExpiredCodeAndThrottlesResend() {
        String code = reachCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) assertThat(send(wrong)).contains("Código incorreto");
        assertThat(send(code)).contains("Limite de tentativas");
        assertThat(send("/reenviar")).contains("60 segundos");
        var pending = registrations.findById(123L).orElseThrow();
        pending.expiresAt = Instant.now().minusSeconds(1);
        assertThat(send(code)).contains("expirou");
        pending.lastSentAt = Instant.now().minusSeconds(61);
        assertThat(send("/reenviar")).contains("Enviamos");
        assertThat(pending.attempts).isZero();
        verify(mail, times(2)).send(eq("italo@example.com"), anyString());
    }

    @Test void mailFailureKeepsDraftAndAllowsRetryWithoutLosingValidCode() {
        send("/start"); send("Italo Silva"); send("italo"); send("SenhaTeste123!");
        doThrow(new MailSendException("SMTP indisponível")).when(mail).send(anyString(), anyString());
        assertThat(send("italo@example.com")).contains("Não foi possível enviar");
        assertThat(registrations.findById(123L).orElseThrow().step).isEqualTo(TelegramRegistration.Step.EMAIL);
        reset(mail);
        send("italo@example.com");
        var code = ArgumentCaptor.forClass(String.class);
        verify(mail).send(anyString(), code.capture());
        registrations.findById(123L).orElseThrow().lastSentAt = Instant.now().minusSeconds(61);
        doThrow(new MailSendException("falha")).when(mail).send(anyString(), anyString());
        assertThat(send("/reenviar")).contains("Não foi possível enviar");
        assertThat(send(code.getValue())).contains("Cadastro concluído");
    }

    @Test void detectsIdentityClaimedBeforeConfirmation() {
        String code = reachCode();
        users.saveAndFlush(new UserEntity("italo", null));
        assertThat(send(code)).contains("já está em uso");
        assertThat(registrations.count()).isZero();
        assertThat(users.findByTelegramUserId(123L)).isEmpty();
    }

    @Test void linksExistingWebAccountWithoutCreatingAnotherUser() {
        var web = userService.create("italo", null, " MinhaSenha123! ");
        var originalId = web.getId();
        assertThat(send("/entrar")).contains("usuário da web");
        assertThat(send("ITALO")).contains("senha da sua conta");
        assertThat(send(" MinhaSenha123! ")).contains("Conta vinculada");
        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findByTelegramUserId(123L).orElseThrow().getId()).isEqualTo(originalId);
        assertThat(registrations.count()).isZero();
        verifyNoInteractions(mail);
    }

    @Test void wrongPasswordCannotLinkAndCancellingDoesNotResetAccountLock() {
        userService.create("italo", null, "MinhaSenha123!");
        send("/entrar"); send("italo");
        for (int i = 0; i < 5; i++) assertThat(send("errada")).contains("Credenciais inválidas");
        send("/cancelar"); send("/entrar"); send("italo");
        assertThat(send("MinhaSenha123!")).contains("Credenciais inválidas");
        assertThat(users.findByTelegramUserId(123L)).isEmpty();
        assertThat(users.findByUsername("italo").orElseThrow().isLoginLocked()).isTrue();
    }

    @Test void refusesReplacingTelegramAlreadyLinkedToAccount() {
        userService.create("italo", 456L, "MinhaSenha123!");
        send("/entrar"); send("italo");
        assertThat(send("MinhaSenha123!")).contains("Credenciais inválidas");
        assertThat(users.findByUsername("italo").orElseThrow().getTelegramUserId()).isEqualTo(456L);
    }

    @Test void missingAccountAndLegacyAccountWithoutPasswordCannotBeLinked() {
        send("/entrar"); send("inexistente");
        assertThat(send("MinhaSenha123!")).contains("Credenciais inválidas");
        users.saveAndFlush(new UserEntity("legado", null));
        send("/entrar"); send("legado");
        assertThat(send("MinhaSenha123!")).contains("Credenciais inválidas");
        assertThat(users.findByTelegramUserId(123L)).isEmpty();
    }

    @Test void refusesRegistrationInGroupsAndRequiresStart() {
        assertThat(service.handle(update("/start", "supergroup", 1))).contains("conversa privada");
        assertThat(send("oi")).contains("/start");
        assertThat(registrations.count()).isZero();
        verifyNoInteractions(mail);
    }
}
