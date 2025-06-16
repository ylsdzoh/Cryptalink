package com.cryptalink.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final int PORT = 8888;
    private static final String VERSION = "1.0";
    private final ExecutorService executorService;
    private boolean running;

    public Server() {
        this.executorService = Executors.newCachedThreadPool();
        this.running = false;
    }

    public void start() {
        running = true;
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("服务器启动成功，监听端口: {}", PORT);
            
            while (running) {
                Socket clientSocket = serverSocket.accept();
                logger.info("新客户端连接: {}", clientSocket.getInetAddress());
                executorService.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            logger.error("服务器运行异常: ", e);
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        running = false;
        executorService.shutdown();
        logger.info("服务器已关闭");
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }
} 