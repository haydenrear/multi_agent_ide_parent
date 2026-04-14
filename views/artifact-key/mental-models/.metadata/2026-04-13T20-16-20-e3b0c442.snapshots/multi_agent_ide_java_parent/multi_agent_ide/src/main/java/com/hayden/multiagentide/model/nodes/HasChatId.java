package com.hayden.multiagentide.model.nodes;

/**
 * Capability mixin for graph nodes that track the chat session key used for LLM communication.
 * The chatSessionKey may differ from the nodeId when sessions are resolved through
 * AiFilterSessionResolver or PromptContext.chatId().
 */
public interface HasChatId {

    /**
     * The chat session key used for LLM communication on this node.
     * May be the same as nodeId, or a child/derived key from session resolution.
     */
    String chatId();
}
