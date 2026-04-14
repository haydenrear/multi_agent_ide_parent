package com.hayden.multiagentide.filter.service;

import com.hayden.multiagentide.filter.model.executor.AiFilterTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Default runtime hydrator for AI filter tools after JSON deserialization.
 */
@Component
public class AiFilterToolHydration implements ApplicationContextAware {

    private ApplicationContext context;

    public <I, O> void hydrate(AiFilterTool<I, O> aiFilterTool) {
        context.getAutowireCapableBeanFactory().autowireBean(aiFilterTool);
    }

    @Autowired
    public void setApplicationContext(ApplicationContext context) {
        this.context = context;
    }
}

