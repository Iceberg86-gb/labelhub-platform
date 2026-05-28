package com.labelhub.api.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.labelhub.api.generated.model.LinkageCondition;
import com.labelhub.api.module.schema.json.LinkageConditionDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Module labelhubJacksonModule() {
        SimpleModule module = new SimpleModule("labelhub-jackson");
        module.addDeserializer(LinkageCondition.class, new LinkageConditionDeserializer());
        return module;
    }
}
