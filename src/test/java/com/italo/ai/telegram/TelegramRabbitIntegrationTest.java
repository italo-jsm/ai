package com.italo.ai.telegram;

import com.italo.ai.config.TelegramRabbitConfiguration;
import com.italo.ai.telegram.model.TelegramUpdate;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.italo.ai.chat.ChatService;
import com.italo.ai.user.UserService;
import static com.italo.ai.support.TestUsers.ALICE;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@EnabledIfEnvironmentVariable(named = "RUN_RABBITMQ_TESTS", matches = "true")
class TelegramRabbitIntegrationTest {

    @Test
    void consumesJsonThroughAnnotatedListenerOnIsolatedQueue() {
        String queueName = "ai.telegram.test." + UUID.randomUUID();
        var chat = mock(ChatService.class);
        var bot = mock(TelegramBotClient.class);
        var users = mock(UserService.class);
        when(users.findByTelegramUserId(1234L)).thenReturn(java.util.Optional.of(ALICE));
        when(chat.chatFromTelegram(ALICE, "Teste isolado", 9876543210L, null)).thenReturn("Resposta");
        var consumer = spy(new TelegramConsumer(chat, bot, users, mock(TelegramRegistrationService.class), java.util.Optional.empty()));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("app.telegram.enabled", "true", "app.telegram.queue", queueName)));
            context.registerBean("connectionFactory", CachingConnectionFactory.class, () -> {
                var connection = new CachingConnectionFactory(env("RABBITMQ_HOST", "localhost"),
                        Integer.parseInt(env("RABBITMQ_PORT", "5672")));
                connection.setUsername(env("RABBITMQ_USER", "nous"));
                connection.setPassword(env("RABBITMQ_PASSWORD", "nous123"));
                connection.setVirtualHost(env("RABBITMQ_VHOST", "nous"));
                return connection;
            });
            context.registerBean("testQueue", Queue.class, () -> new Queue(queueName, false, true, true));
            context.registerBean("rabbitAdmin", RabbitAdmin.class,
                    () -> new RabbitAdmin(context.getBean(CachingConnectionFactory.class)));
            context.registerBean("rabbitListenerContainerFactory", SimpleRabbitListenerContainerFactory.class, () -> {
                var factory = new SimpleRabbitListenerContainerFactory();
                factory.setConnectionFactory(context.getBean(CachingConnectionFactory.class));
                return factory;
            });
            context.registerBean("telegramConsumer", TelegramConsumer.class, () -> consumer);
            context.register(RabbitTestConfiguration.class, TelegramRabbitConfiguration.class);
            context.refresh();

            var properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            var body = """
                    {"update_id":123,"message":{"message_id":7,
                    "from":{"id":1234,"is_bot":false},"chat":{"id":9876543210,"type":"private"},"text":"Teste isolado"}}
                    """;
            new RabbitTemplate(context.getBean(CachingConnectionFactory.class))
                    .send("", queueName, new Message(body.getBytes(StandardCharsets.UTF_8), properties));

            verify(consumer, timeout(10000)).consume(argThat((TelegramUpdate update) ->
                    update.updateId() == 123L && update.message().chat().id() == 9876543210L
                            && "Teste isolado".equals(update.message().text())));
            verify(bot, timeout(10000)).sendMessage(9876543210L, 7L, "Resposta");
        }
    }

    private static String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableRabbit
    static class RabbitTestConfiguration {}
}
