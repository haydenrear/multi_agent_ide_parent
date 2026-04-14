package com.hayden.acp_cdc_ai.acp;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

public interface ChatMemoryContext {

    List<Message> getMessages(Object memoryId);
}
