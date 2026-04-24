package com.example.fileagent.skill;

import java.io.File;
import java.util.List;

public interface FileSkill {
    String getName();
    String getDescription();
    String execute(String... args);
}
