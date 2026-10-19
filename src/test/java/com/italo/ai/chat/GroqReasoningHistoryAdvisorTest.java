package com.italo.ai.chat;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.*;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroqReasoningHistoryAdvisorTest {
    @Test void removesOnlyReasoningWithoutMutatingOriginalMessageOrToolCalls() {
        var toolCall = new AssistantMessage.ToolCall("call-1", "function", "searchWeb", "{\"query\":\"Fernando\"}");
        var original = AssistantMessage.builder().content("Vou pesquisar.")
            .properties(Map.of("reasoningContent", "metadado simulado", "keep", "value"))
            .toolCalls(List.of(toolCall)).build();
        var user = new UserMessage("Quem foi Fernando?");
        var options = ChatOptions.builder().temperature(0.2).build();
        var request = new ChatClientRequest(new Prompt(List.of(user, original), options), Map.of("keep", "context"));
        var chain = mock(CallAdvisorChain.class);
        new GroqReasoningHistoryAdvisor().adviseCall(request, chain);
        var forwarded = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(forwarded.capture());
        var result = (AssistantMessage) forwarded.getValue().prompt().getInstructions().getLast();
        assertThat(result.getMetadata()).containsEntry("keep", "value").doesNotContainKey("reasoningContent");
        assertThat(result.getText()).isEqualTo("Vou pesquisar.");
        assertThat(result.getToolCalls()).containsExactly(toolCall);
        assertThat(original.getMetadata()).containsKey("reasoningContent");
        assertThat(forwarded.getValue().prompt().getInstructions().getFirst()).isSameAs(user);
        assertThat(forwarded.getValue().prompt().getOptions()).isEqualTo(options);
        assertThat(forwarded.getValue().context()).containsEntry("keep", "context");
    }
}
