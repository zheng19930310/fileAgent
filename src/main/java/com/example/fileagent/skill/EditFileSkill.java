package com.example.fileagent.skill;

import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

@Component
public class EditFileSkill implements FileSkill {

    @Override
    public String getName() {
        return "edit_file";
    }

    @Override
    public String getDescription() {
        return "编辑文件内容，参数: 文件路径 [新内容]";
    }

    @Override
    public String execute(String... args) {
        if (args == null || args.length < 2) {
            return "错误: 请提供文件路径和新内容";
        }

        String path = args[0];
        String content = args[1];

        File file = new File(path);

        if (!file.exists()) {
            return "错误: 文件不存在 - " + path;
        }

        if (file.isDirectory()) {
            return "错误: 路径是目录，不是文件 - " + path;
        }

        try {
            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();

            return String.format("成功更新文件: %s", path);
        } catch (IOException e) {
            return "错误: 编辑文件失败 - " + e.getMessage();
        }
    }
}
