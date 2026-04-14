package com.hayden.multiagentide.propagation.service;

import com.hayden.multiagentide.model.executor.AiPropagatorTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class AiPropagatorToolHydration implements ApplicationContextAware {

    private ApplicationContext context;

    public void hydrate(AiPropagatorTool aiPropagatorTool) {
        context.getAutowireCapableBeanFactory().autowireBean(aiPropagatorTool);
    }

    @Autowired
    public void setApplicationContext(ApplicationContext context) {
        this.context = context;
    }
}
