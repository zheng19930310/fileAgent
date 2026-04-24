package com.example.fileagent.skill;

import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;

@Component
public class ListFilesSkill implements FileSkill {

    @Override
    public String getName() {
        return "list_files";
    }

    @Override
    public String getDescription() {
        return "列出指定目录下的文件和子目录";
    }

    @Override
    public String execute(String... args) {
        if (args == null || args.length == 0) {
            return "错误: 请提供目录路径";
        }

        String path = args[0];
        File dir = new File(path);

        if (!dir.exists()) {
            return "错误: 目录不存在 - " + path;
        }

        if (!dir.isDirectory()) {
            return "错误: 路径不是目录 - " + path;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return "错误: 无法读取目录 - " + path;
        }

        StringBuilder sb = new StringBuilder(String.format("目录 %s 的内容:\n", path));
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File file : files) {
            String type = file.isDirectory() ? "[DIR]  " : "[FILE] ";
            long size = file.isFile() ? file.length() : 0;
            String sizeStr = formatSize(size);
            sb.append(String.format("  %s %-30s %s\n", type, file.getName(), sizeStr));
        }

        return sb.toString();
    }

    private String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}
