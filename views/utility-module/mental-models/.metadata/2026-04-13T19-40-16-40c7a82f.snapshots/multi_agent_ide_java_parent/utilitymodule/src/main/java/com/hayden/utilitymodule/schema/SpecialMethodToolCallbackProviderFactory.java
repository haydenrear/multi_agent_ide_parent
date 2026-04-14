package com.hayden.utilitymodule.schema;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpecialMethodToolCallbackProviderFactory {

    /**
     * Allows for templating items in the spring Tool and ToolParam decorator annotations
     * @param codeSearchMcpTools
     * @param specialJsonSchemaGenerator
     * @param schemaReplacer
     * @return
     */
    public SpecialMethodToolCallbackProvider createToolCallbackProvider(List<Object> codeSearchMcpTools,
                                                                        SpecialJsonSchemaGenerator specialJsonSchemaGenerator,
                                                                        DelegatingSchemaReplacer schemaReplacer,
                                                                        ApplicationContext ctx) {
        SpecialMethodToolCallbackProvider specialMethodToolCallbackProvider
                = new SpecialMethodToolCallbackProvider(codeSearchMcpTools, specialJsonSchemaGenerator, schemaReplacer, ctx);
        specialMethodToolCallbackProvider.setApplicationContext(ctx);
        return specialMethodToolCallbackProvider;
    }

}
