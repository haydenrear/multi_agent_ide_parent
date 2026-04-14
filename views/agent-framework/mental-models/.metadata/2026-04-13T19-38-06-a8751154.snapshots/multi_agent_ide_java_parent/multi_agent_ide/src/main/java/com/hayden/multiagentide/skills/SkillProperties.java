package com.hayden.multiagentide.skills;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Data
@ConfigurationProperties(prefix = "skills")
public class SkillProperties {

    public record LocalSkillDescriptor(Path skillPath) {}

    Map<String, List<LocalSkillDescriptor>> localSkills = new HashMap<>();

}
