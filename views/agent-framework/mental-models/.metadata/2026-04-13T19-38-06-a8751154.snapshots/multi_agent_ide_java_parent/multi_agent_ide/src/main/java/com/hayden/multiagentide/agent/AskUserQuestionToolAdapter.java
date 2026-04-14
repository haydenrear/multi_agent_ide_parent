package com.hayden.multiagentide.agent;

import com.hayden.commitdiffcontext.cdc_utils.SetFromHeader;
import com.hayden.commitdiffcontext.mcp.ToolCarrier;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.hayden.acp_cdc_ai.acp.AcpChatModel.MCP_SESSION_HEADER;

@Component
@RequiredArgsConstructor
public class AskUserQuestionToolAdapter implements ToolCarrier {

    private final AskUserQuestionTool delegate;

    @Tool(name = "AskUserQuestionTool",
			description = """
					Use this tool when you need to ask the user questions during execution. This allows you to:
					1. Gather user preferences or requirements
					2. Clarify ambiguous instructions
					3. Get decisions on implementation choices as you work
					4. Offer choices to the user about what direction to take.

					Usage notes:
					- Users will always be able to select "Other" to provide custom text input
					- Use multiSelect: true to allow multiple answers to be selected for a question
					- If you recommend a specific option, make that the first option in the list and add "(Recommended)" at the end of the label
					""")
	@McpTool(name = "AskUserQuestionTool",
			description = """
					Use this tool when you need to ask the user questions during execution. This allows you to:
					1. Gather user preferences or requirements
					2. Clarify ambiguous instructions
					3. Get decisions on implementation choices as you work
					4. Offer choices to the user about what direction to take.

					Usage notes:
					- Users will always be able to select "Other" to provide custom text input
					- Use multiSelect: true to allow multiple answers to be selected for a question
					- If you recommend a specific option, make that the first option in the list and add "(Recommended)" at the end of the label
					""")
    public String AskUserQuestionTool(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            @ToolParam(description = """
                    Ask the user questions.
                    """)
			@McpToolParam(description = """
                    Ask the user questions.
                    """)
            List<AskUserQuestionTool.Question> questions) {
        return delegate.askUserQuestion(sessionId, questions);
    }

}
