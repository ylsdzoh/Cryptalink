package com.cryptalink.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.Map;
import java.util.UUID;

public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final int PORT = 8888;
    private static final String VERSION = "1.0";
    
    private ServerSocket serverSocket;
    private final ExecutorService executorService;
    private Map<String, Socket> clients;
    private boolean running;
    private ServerEventHandler eventHandler;
    private final DatabaseManager dbManager;
    
    public Server() {
        this.executorService = Executors.newCachedThreadPool();
        this.clients = new ConcurrentHashMap<>();
        this.running = false;
        this.dbManager = DatabaseManager.getInstance();
    }
    
    public void setEventHandler(ServerEventHandler handler) {
        this.eventHandler = handler;
    }
    
    public void start() throws IOException {
        if (running) {
            return;
        }
        
        serverSocket = new ServerSocket(PORT);
        running = true;
        logger.info("服务器启动成功，监听端口: {}", PORT);
        
        // 启动接受客户端连接的线程
        executorService.execute(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleNewClient(clientSocket);
                } catch (IOException e) {
                    if (running) {
                        logger.error("接受客户端连接时发生错误", e);
                        if (eventHandler != null) {
                            eventHandler.onError("接受客户端连接时发生错误: " + e.getMessage());
                        }
                    }
                }
            }
        });
    }
    
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        try {
            // 关闭所有客户端连接
            for (Map.Entry<String, Socket> entry : clients.entrySet()) {
                try {
                    entry.getValue().close();
                } catch (IOException e) {
                    logger.error("关闭客户端连接时发生错误", e);
                }
                if (eventHandler != null) {
                    eventHandler.onClientDisconnected(entry.getKey());
                }
            }
            clients.clear();
            
            // 关闭服务器socket
            if (serverSocket != null) {
                serverSocket.close();
            }
            
            // 关闭线程池
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
            
            logger.info("服务器已关闭");
        } catch (IOException e) {
            logger.error("停止服务器时发生错误", e);
            if (eventHandler != null) {
                eventHandler.onError("停止服务器时发生错误: " + e.getMessage());
            }
        }
    }
    
    private void handleNewClient(Socket clientSocket) {
        String clientId = UUID.randomUUID().toString();
        clients.put(clientId, clientSocket);
        logger.info("新客户端连接: {} (ID: {})", clientSocket.getInetAddress(), clientId);
        
        if (eventHandler != null) {
            eventHandler.onClientConnected(clientId);
        }
        
        // 启动处理客户端消息的线程
        executorService.execute(() -> {
            try {
                handleClientCommunication(clientId, clientSocket);
            } catch (IOException e) {
                logger.error("处理客户端通信时发生错误", e);
                if (eventHandler != null) {
                    eventHandler.onError("处理客户端通信时发生错误: " + e.getMessage());
                }
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    logger.error("关闭客户端socket时发生错误", e);
                }
                clients.remove(clientId);
                if (eventHandler != null) {
                    eventHandler.onClientDisconnected(clientId);
                }
            }
        });
    }
    
    private void handleClientCommunication(String clientId, Socket clientSocket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        
        // 创建uploads目录（如果不存在）
        File uploadsDir = new File("uploads");
        if (!uploadsDir.exists()) {
            uploadsDir.mkdir();
        }

        while (running) {
            try {
                String line = in.readLine();
                if (line == null) {
                    break;  // 客户端断开连接
                }

                if (line.startsWith("VERSION_CHECK")) {
                    // 发送版本信息
                    out.println("VERSION:" + VERSION);
                } else if (line.startsWith("GET_UPDATE_URL")) {
                    // 发送更新URL
                    out.println("UPDATE_URL:https://example.com/update");
                } else if (line.startsWith("UPLOAD:")) {
                    // 处理文件上传
                    String filename = line.substring("UPLOAD:".length());
                    handleFileUpload(filename, in, out, uploadsDir);
                }
            } catch (IOException e) {
                if (running) {
                    throw e;
                }
                break;
            }
        }
    }
    
    private void handleFileUpload(String filename, BufferedReader in, PrintWriter out, File uploadsDir) throws IOException {
        logger.info("开始接收文件: {}", filename);
        StringBuilder base64Content = new StringBuilder();
        String line;
        
        // 读取Base64编码的文件内容
        while ((line = in.readLine()) != null) {
            if (line.equals("END_UPLOAD")) {
                break;
            }
            base64Content.append(line);
        }
        
        try {
            // 解码Base64内容
            byte[] fileContent = org.apache.commons.codec.binary.Base64.decodeBase64(base64Content.toString());
            
            // 保存文件
            File file = new File(uploadsDir, filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(fileContent);
            }
            
            // 检查是否是BMP文件并进行隐写检测
            boolean hasSteg = false;
            String hiddenMessage = null;
            if (filename.toLowerCase().endsWith(".bmp")) {
                hasSteg = LSBSteganography.hasSteg(file.getPath());
                if (hasSteg) {
                    hiddenMessage = "检测到隐写信息";
                }
            }
            
            // 保存文件信息到数据库
            dbManager.saveFileInfo(filename, hasSteg, hiddenMessage);
            
            out.println("UPLOAD_SUCCESS");
            logger.info("文件接收完成: {}", filename);
            
            if (eventHandler != null) {
                eventHandler.onFileReceived(filename);
            }
        } catch (Exception e) {
            logger.error("处理文件上传失败", e);
            out.println("UPLOAD_FAILED:" + e.getMessage());
        }
    }
}