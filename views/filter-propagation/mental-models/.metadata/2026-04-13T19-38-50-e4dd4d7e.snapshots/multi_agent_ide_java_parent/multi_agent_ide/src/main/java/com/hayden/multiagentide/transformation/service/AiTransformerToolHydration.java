package com.hayden.multiagentide.transformation.service;

import com.hayden.multiagentide.model.executor.AiTransformerTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class AiTransformerToolHydration implements ApplicationContextAware {

    private ApplicationContext context;

    public void hydrate(AiTransformerTool aiTransformerTool) {
        context.getAutowireCapableBeanFactory().autowireBean(aiTransformerTool);
    }

    @Autowired
    public void setApplicationContext(ApplicationContext context) {
        this.context = context;
    }
}
