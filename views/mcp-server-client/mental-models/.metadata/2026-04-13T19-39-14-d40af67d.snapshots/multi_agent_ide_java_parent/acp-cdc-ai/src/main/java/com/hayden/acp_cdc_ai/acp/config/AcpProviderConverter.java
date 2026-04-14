package com.hayden.acp_cdc_ai.acp.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@ConfigurationPropertiesBinding
public class AcpProviderConverter implements Converter<String, AcpProvider> {

    @Override
    public AcpProvider convert(String source) {
        return AcpProvider.fromWireValue(source);
    }
}
