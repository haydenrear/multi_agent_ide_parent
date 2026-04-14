package com.hayden.multiagentide.skills;

import com.hayden.utilitymodule.stream.StreamUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SkillFinder {

    private final SkillProperties skillProperties;

    private final SkillLoader skillLoader;

    public Optional<SkillDecorator> findSkillDecorator(String name) {
        return findSkillDecorator(name, "Loading skill group for agent named %s.".formatted(name));
    }

    /**
     * @param name name of the agent/skill group for which the skill should be loaded
     * @return optional if skills enabled for that id
     */
    public Optional<SkillDecorator> findSkillDecorator(String name, String description) {
        var s = skillLoader.loadSkill(name, description);
        boolean didLoadSkill = false;
        for(var g : StreamUtil.toStream(skillProperties.localSkills.get(name)).toList()) {
            s = s.withLocalSkill(g.skillPath().toString());
            didLoadSkill = true;
        }

        if (didLoadSkill)
            return Optional.of(new SkillDecorator(s));

        return Optional.empty();
    }

}
