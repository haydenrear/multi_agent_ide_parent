package com.hayden.utilitymodule.ctx;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
@Component
@Slf4j
public class PresetProperties implements ApplicationContextAware, BeanFactoryPostProcessor, Ordered {


    private ApplicationContext ctx;
    private ConfigurationPropertiesBindingPostProcessor processor;

    @SneakyThrows
    @Override
    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.ctx = applicationContext;
    }

    @Override
    @SneakyThrows
    public void postProcessBeanFactory(ConfigurableListableBeanFactory b) throws BeansException {
        processor = new ConfigurationPropertiesBindingPostProcessor();
        processor.setApplicationContext(this.ctx);
        processor.afterPropertiesSet();
        b.getBeansWithAnnotation(PreSetConfigurationProperties.class)
                .forEach((beanName, bean) -> doSetValues(bean, beanName));
    }

    public void doSetValues(Object n, String simpleName) {
        processor.postProcessBeforeInitialization(n, simpleName);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
