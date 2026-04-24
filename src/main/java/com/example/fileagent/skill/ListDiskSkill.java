package com.example.fileagent.skill;

import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;

@Component
public class ListDiskSkill implements FileSkill {

    @Override
    public String getName() {
        return "list_disk";
    }

    @Override
    public String getDescription() {
        return "列出系统所有磁盘驱动器";
    }

    @Override
    public String execute(String... args) {
        File[] roots = File.listRoots();
        StringBuilder sb = new StringBuilder("系统磁盘列表:\n");
        for (File root : roots) {
            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getFreeSpace();
            long usableSpace = root.getUsableSpace();

            sb.append(String.format("  盘符: %s\n", root.getAbsolutePath()));
            sb.append(String.format("    总容量: %.2f GB\n", totalSpace / (1024.0 * 1024 * 1024)));
            sb.append(String.format("    可用空间: %.2f GB\n", usableSpace / (1024.0 * 1024 * 1024)));
            sb.append(String.format("    空闲空间: %.2f GB\n", freeSpace / (1024.0 * 1024 * 1024)));
            sb.append("\n");
        }
        return sb.toString();
    }
}
