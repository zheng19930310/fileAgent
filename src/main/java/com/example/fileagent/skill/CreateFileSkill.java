package com.example.fileagent.skill;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@Component
public class CreateFileSkill implements FileSkill {

    @Override
    public String getName() {
        return "create_file";
    }

    @Override
    public String getDescription() {
        return "创建新文件，参数: 文件路径 [内容]";
    }

    @Override
    public String execute(String... args) {
        if (args == null || args.length == 0) {
            return "错误: 请提供文件路径";
        }

        String path = args[0];
        String content = args.length > 1 ? args[1] : "";

        try {
            File file = new File(path);

            // 确保父目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            if (file.exists()) {
                return "错误: 文件已存在 - " + path;
            }

            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();

            return String.format("成功创建文件: %s", path);
        } catch (IOException e) {
            return "错误: 创建文件失败 - " + e.getMessage();
        }
    }
}
