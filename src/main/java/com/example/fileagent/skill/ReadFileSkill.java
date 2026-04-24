package com.example.fileagent.skill;

import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

@Component
public class ReadFileSkill implements FileSkill {

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "读取文件内容，参数: 文件路径 [最大行数]";
    }

    @Override
    public String execute(String... args) {
        if (args == null || args.length == 0) {
            return "错误: 请提供文件路径";
        }

        String path = args[0];
        int maxLines = args.length > 1 ? Integer.parseInt(args[1]) : 100;

        File file = new File(path);

        if (!file.exists()) {
            return "错误: 文件不存在 - " + path;
        }

        if (file.isDirectory()) {
            return "错误: 路径是目录，不是文件 - " + path;
        }

        try {
            String content = new String(Files.readAllBytes(Paths.get(path)));
            String[] lines = content.split("\n");

            StringBuilder sb = new StringBuilder(String.format("文件 %s 的内容:\n", path));
            sb.append("---\n");

            int displayLines = Math.min(lines.length, maxLines);
            for (int i = 0; i < displayLines; i++) {
                sb.append(lines[i]).append("\n");
            }

            if (lines.length > maxLines) {
                sb.append(String.format("\n... (还有 %d 行未显示)", lines.length - maxLines));
            }

            return sb.toString();
        } catch (Exception e) {
            return "错误: 读取文件失败 - " + e.getMessage();
        }
    }
}
