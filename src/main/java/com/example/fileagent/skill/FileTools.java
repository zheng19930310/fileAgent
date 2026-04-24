package com.example.fileagent.skill;

import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * 文件操作工具函数 - 用于AI Function Calling
 * 通过Spring AI的Function Callback机制，让AI自主调用文件操作技能
 */
@Component
public class FileTools {

    private final SkillManager skillManager;

    public FileTools(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    /**
     * 查看磁盘信息
     */
    public String listDisk(String params) {
        return skillManager.executeSkill("list_disk");
    }

    /**
     * 列出目录内容
     * @param path 目录路径
     */
    public String listFiles(String path) {
        return skillManager.executeSkill("list_files", path);
    }

    /**
     * 获取文件大小
     * @param path 文件或目录路径
     */
    public String getFileSize(String path) {
        return skillManager.executeSkill("get_file_size", path);
    }

    /**
     * 读取文件内容
     * @param path 文件路径
     * @param maxLines 最大行数（可选）
     */
    public String readFile(String path, Integer maxLines) {
        if (maxLines != null) {
            return skillManager.executeSkill("read_file", path, String.valueOf(maxLines));
        }
        return skillManager.executeSkill("read_file", path);
    }

    /**
     * 创建文件
     * @param path 文件路径
     * @param content 文件内容
     */
    public String createFile(String path, String content) {
        if (content == null) {
            content = "";
        }
        return skillManager.executeSkill("create_file", path, content);
    }

    /**
     * 编辑/覆盖文件内容
     * @param path 文件路径
     * @param content 新内容
     */
    public String editFile(String path, String content) {
        return skillManager.executeSkill("edit_file", path, content);
    }

    /**
     * 删除文件或目录
     * @param path 文件或目录路径
     */
    public String deleteFile(String path) {
        return skillManager.executeSkill("delete_file", path);
    }
}
