package com.example.fileagent.skill;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SkillManager {

    @Autowired
    private List<FileSkill> skills;

    private Map<String, FileSkill> skillMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (FileSkill skill : skills) {
            skillMap.put(skill.getName(), skill);
        }
    }

    public String executeSkill(String skillName, String... args) {
        FileSkill skill = skillMap.get(skillName);
        if (skill == null) {
            return "错误: 未知的技能 - " + skillName;
        }
        return skill.execute(args);
    }

    public String getAllSkillsDescription() {
        return skillMap.values().stream()
                .map(skill -> String.format("- %s: %s", skill.getName(), skill.getDescription()))
                .collect(Collectors.joining("\n"));
    }

    public Map<String, FileSkill> getSkillMap() {
        return skillMap;
    }
}
