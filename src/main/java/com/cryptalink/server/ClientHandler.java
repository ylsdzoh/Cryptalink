package com.cryptalink.server;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final Socket clientSocket;
    private final String uploadDir = "uploads";
    private final DatabaseManager dbManager;
    private static final String UPDATE_URL = "http://your-server.com/updates/cryptalink-client-jar-with-dependencies.jar";

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.dbManager = DatabaseManager.getInstance();
        createUploadDirectory();
    }

    private void createUploadDirectory() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            logger.error("创建上传目录失败: ", e);
        }
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if ("VERSION_CHECK".equals(inputLine)) {
                    handleVersionCheck(out);
                } else if (inputLine.startsWith("UPLOAD:")) {
                    handleFileUpload(in, out, inputLine.substring(7));
                } else if ("GET_UPDATE_URL".equals(inputLine)) {
                    handleUpdateRequest(out);
                }
            }
        } catch (IOException e) {
            logger.error("客户端处理异常: ", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.error("关闭客户端连接失败: ", e);
            }
        }
    }

    private void handleVersionCheck(PrintWriter out) {
        out.println("VERSION:1.0");
    }

    private void handleUpdateRequest(PrintWriter out) {
        out.println("UPDATE_URL:" + UPDATE_URL);
        logger.info("发送更新链接给客户端");
    }

    private void handleFileUpload(BufferedReader in, PrintWriter out, String fileName) throws IOException {
        // 读取Base64编码的文件内容
        StringBuilder content = new StringBuilder();
        String line;
        while (!(line = in.readLine()).equals("END_UPLOAD")) {
            content.append(line);
        }

        // 解码并保存文件
        byte[] decodedBytes = Base64.decodeBase64(content.toString());
        Path filePath = Paths.get(uploadDir, fileName);
        Files.write(filePath, decodedBytes);

        // 如果是BMP文件，进行LSB隐写处理
        if (fileName.toLowerCase().endsWith(".bmp")) {
            String hiddenMessage = "这是一条隐藏消息 - " + System.currentTimeMillis();
            String bmpFilePath = filePath.toString();
            
            // 进行LSB隐写
            LSBSteganography.hideMessage(bmpFilePath, hiddenMessage);
            
            // 保存到数据库
            dbManager.saveFileInfo(fileName, true, hiddenMessage);
            
            out.println("UPLOAD_SUCCESS:STEGANOGRAPHY");
            logger.info("文件 {} 上传成功并完成隐写处理", fileName);
        } else {
            // 普通文件直接保存到数据库
            dbManager.saveFileInfo(fileName, false, null);
            out.println("UPLOAD_SUCCESS");
            logger.info("文件 {} 上传成功", fileName);
        }
    }
} 