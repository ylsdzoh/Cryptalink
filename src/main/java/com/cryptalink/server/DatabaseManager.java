package com.cryptalink.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DB_URL = "jdbc:sqlite:cryptalink.db";
    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {
        initializeDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void initializeDatabase() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            createTables();
            logger.info("数据库初始化成功");
        } catch (SQLException e) {
            logger.error("数据库初始化失败: ", e);
        }
    }

    private void createTables() throws SQLException {
        String createFileInfoTable = """
            CREATE TABLE IF NOT EXISTS file_info (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                filename TEXT NOT NULL,
                upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                has_steganography BOOLEAN DEFAULT FALSE,
                hidden_message TEXT
            )
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createFileInfoTable);
        }
    }

    public void saveFileInfo(String filename, boolean hasSteganography, String hiddenMessage) {
        String sql = "INSERT INTO file_info (filename, has_steganography, hidden_message) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, filename);
            pstmt.setBoolean(2, hasSteganography);
            pstmt.setString(3, hiddenMessage);
            pstmt.executeUpdate();
            logger.info("文件信息已保存到数据库: {}", filename);
        } catch (SQLException e) {
            logger.error("保存文件信息失败: ", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.error("关闭数据库连接失败: ", e);
        }
    }
} 