package com.cryptalink.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class VersionManager {
    private static final Logger logger = LoggerFactory.getLogger(VersionManager.class);
    private static final String VERSION_FILE = "/version.properties";
    private static VersionManager instance;
    private final Properties properties;

    private VersionManager() {
        properties = new Properties();
        loadVersion();
    }

    public static synchronized VersionManager getInstance() {
        if (instance == null) {
            instance = new VersionManager();
        }
        return instance;
    }

    private void loadVersion() {
        try (InputStream input = getClass().getResourceAsStream(VERSION_FILE)) {
            if (input != null) {
                properties.load(input);
                logger.info("版本信息加载成功: {}", getVersion());
            } else {
                logger.error("无法找到版本配置文件");
            }
        } catch (IOException e) {
            logger.error("加载版本信息失败: ", e);
        }
    }

    public String getVersion() {
        return properties.getProperty("version", "unknown");
    }

    public String getBuildDate() {
        return properties.getProperty("build.date", "unknown");
    }

    public String getUpdateUrl() {
        return properties.getProperty("update.url", "");
    }

    public boolean isNewerVersion(String otherVersion) {
        String currentVersion = getVersion();
        return compareVersions(currentVersion, otherVersion) < 0;
    }

    private int compareVersions(String version1, String version2) {
        String[] v1 = version1.split("\\.");
        String[] v2 = version2.split("\\.");
        
        int length = Math.max(v1.length, v2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < v1.length ? Integer.parseInt(v1[i]) : 0;
            int num2 = i < v2.length ? Integer.parseInt(v2[i]) : 0;
            
            if (num1 < num2) return -1;
            if (num1 > num2) return 1;
        }
        return 0;
    }
} 