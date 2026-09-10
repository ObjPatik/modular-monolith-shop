package edu.cit.verano;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@SpringBootApplication
public class ShopApplication {

    public static void main(String[] args) {
        loadDotenv();
        SpringApplication.run(ShopApplication.class, args);
    }

    /**
     * Helper to load .env file from working directory or parent directory
     * into System properties if not already set in the environment.
     */
    private static void loadDotenv() {
        String[] possiblePaths = {".env", "../.env", "backend/.env"};
        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                try {
                    List<String> lines = Files.readAllLines(Paths.get(file.toURI()));
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                            continue;
                        }
                        int eqIdx = line.indexOf('=');
                        String key = line.substring(0, eqIdx).trim();
                        String value = line.substring(eqIdx + 1).trim();
                        // strip surrounding quotes if present
                        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                    System.out.println("[ShopApplication] Loaded environment configuration from: " + file.getAbsolutePath());
                    break;
                } catch (IOException ignored) {
                }
            }
        }
    }
}
