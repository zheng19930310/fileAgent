package com.example.fileagent.skill;

import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class GetFileSizeSkill implements FileSkill {

    @Override
    public String getName() {
        return "get_file_size";
    }

    @Override
    public String getDescription() {
        return "获取指定文件的大小";
    }

    @Override
    public String execute(String... args) {
        if (args == null || args.length == 0) {
            return "错误: 请提供文件路径";
        }

        String path = args[0];
        File file = new File(path);

        if (!file.exists()) {
            return "错误: 文件不存在 - " + path;
        }

        if (file.isDirectory()) {
            long dirSize = calculateDirSize(file);
            return String.format("目录 %s 的总大小: %.2f MB", path, dirSize / (1024.0 * 1024));
        }

        long size = file.length();
        return String.format("文件 %s 的大小: %s", path, formatSize(size));
    }

    private long calculateDirSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += calculateDirSize(file);
                }
            }
        }
        return size;
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
