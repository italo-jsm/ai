package com.italo.ai.chat;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import java.util.HashMap;

/** Spring AI 2.0.1 replays reasoning as a field Groq does not accept in assistant history. */
public class GroqReasoningHistoryAdvisor implements CallAdvisor {
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        var messages = request.prompt().getInstructions().stream().map(this::withoutReasoning).toList();
        var prompt = request.prompt().mutate().messages(messages).build();
        return chain.nextCall(request.mutate().prompt(prompt).build());
    }

    private Message withoutReasoning(Message message) {
        if (!(message instanceof AssistantMessage assistant)
                || !assistant.getMetadata().containsKey("reasoningContent")) return message;
        var metadata = new HashMap<>(assistant.getMetadata());
        metadata.remove("reasoningContent");
        return assistant.mutate().properties(metadata).build();
    }

    @Override
    public String getName() {
        return "Groq reasoning history compatibility";
    }

    @Override
    public int getOrder() {
        // Run inside the tool loop, before every model invocation, including subsequent rounds.
        return ToolCallingAdvisor.DEFAULT_ORDER + 1;
    }
}
