package com.pxbtdev.repository;

import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ScriptRepository {

    private final String scriptDirectory;

    public ScriptRepository() {
        this.scriptDirectory = "./generated-scripts";
        ensureDirectoryExists();
    }

    public ScriptRepository(String scriptDirectory) {
        this.scriptDirectory = scriptDirectory;
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        try {
            Path dirPath = Paths.get(scriptDirectory);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create script directory: " + scriptDirectory, e);
        }
    }

    public void saveScript(String filename, String content) throws IOException {
        Path filePath = Paths.get(scriptDirectory, filename);
        Files.writeString(filePath, content);
    }

    public String getScript(String filename) throws IOException {
        Path filePath = Paths.get(scriptDirectory, filename);
        if (!Files.exists(filePath)) {
            throw new IOException("Script not found: " + filename);
        }
        return Files.readString(filePath);
    }

    public boolean deleteScript(String filename) {
        try {
            Path filePath = Paths.get(scriptDirectory, filename);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete script: " + filename, e);
        }
    }

    public List<String> listAllScripts() {
        try {
            Path dirPath = Paths.get(scriptDirectory);
            if (!Files.exists(dirPath)) {
                return new ArrayList<>();
            }

            return Files.list(dirPath)
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(".js"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to list scripts", e);
        }
    }

    public boolean scriptExists(String filename) {
        Path filePath = Paths.get(scriptDirectory, filename);
        return Files.exists(filePath);
    }

    public long getScriptCount() {
        try {
            Path dirPath = Paths.get(scriptDirectory);
            if (!Files.exists(dirPath)) {
                return 0;
            }
            return Files.list(dirPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".js"))
                    .count();
        } catch (IOException e) {
            throw new RuntimeException("Failed to count scripts", e);
        }
    }
}