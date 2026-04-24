package com.example.fileagent.skill;

import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class DeleteFileSkill implements FileSkill {

    @Override
    public String getName() {
        return "delete_file";
    }

    @Override
    public String getDescription() {
        return "删除指定文件或目录，参数: 文件路径";
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

        boolean deleted = deleteRecursive(file);
        if (deleted) {
            return String.format("成功删除: %s", path);
        } else {
            return "错误: 删除失败 - " + path;
        }
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }
}
