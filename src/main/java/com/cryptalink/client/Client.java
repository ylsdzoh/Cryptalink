package com.cryptalink.client;

import com.cryptalink.common.VersionManager;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cryptalink.server.LSBSteganography;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8888;
    private static final String CLIENT_JAR_NAME = "cryptalink-client-jar-with-dependencies.jar";
    
    private final ExecutorService executorService;
    private final VersionManager versionManager;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean running;

    public Client() {
        this.executorService = Executors.newCachedThreadPool();
        this.versionManager = VersionManager.getInstance();
        this.running = false;
    }

    public void start() {
        try {
            connect();
            checkVersion();
            running = true;
            
            // 启动用户交互线程
            executorService.execute(this::handleUserInput);
            
            // 主线程处理服务器响应
            handleServerResponses();
        } catch (IOException e) {
            logger.error("客户端运行异常: ", e);
        } finally {
            shutdown();
        }
    }

    private void connect() throws IOException {
        socket = new Socket(SERVER_HOST, SERVER_PORT);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
        logger.info("已连接到服务器");
    }

    private void checkVersion() throws IOException {
        out.println("VERSION_CHECK");
        String response = in.readLine();
        if (response != null && response.startsWith("VERSION:")) {
            String serverVersion = response.substring(8);
            if (versionManager.isNewerVersion(serverVersion)) {
                logger.info("发现新版本 {}，当前版本 {}，准备更新...", serverVersion, versionManager.getVersion());
                // 获取新下载链接
                out.println("GET_UPDATE_URL");
                String updateUrl = in.readLine();
                if (updateUrl != null && updateUrl.startsWith("UPDATE_URL:")) {
                    String url = updateUrl.substring(11);
                    downloadAndUpdate(url);
                }
            } else {
                logger.info("当前版本 {} 已是最新", versionManager.getVersion());
            }
        }
    }

    private void downloadAndUpdate(String updateUrl) {
        try {
            logger.info("开始下载新版本...");
            // 下载新版本
            URL url = new URL(updateUrl);
            Path tempFile = Files.createTempFile("new-client-", ".jar");
            try (InputStream in = url.openStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // 获取当前JAR文件路径
            String currentJarPath = new File(Client.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
            Path currentJar = Paths.get(currentJarPath);

            // 创建更新脚本
            Path updateScript = createUpdateScript(currentJar, tempFile);

            // 执行更新脚本
            logger.info("准备重启新版本...");
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", updateScript.toString());
            pb.start();

            // 关闭当前程序
            System.exit(0);
        } catch (Exception e) {
            logger.error("更新失败: ", e);
        }
    }

    private Path createUpdateScript(Path currentJar, Path newJar) throws IOException {
        // 创建批处理脚本
        Path script = Files.createTempFile("update-", ".bat");
        try (BufferedWriter writer = Files.newBufferedWriter(script)) {
            writer.write("@echo off\n");
            writer.write("timeout /t 2 /nobreak > nul\n"); // 等待2秒确保当前程序完全退出
            writer.write("copy /Y \"" + newJar + "\" \"" + currentJar + "\"\n");
            writer.write("start javaw -jar \"" + currentJar + "\"\n");
            writer.write("del \"" + script + "\"\n"); // 删除脚本自身
        }
        return script;
    }

    private void handleUserInput() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            System.out.println("\n请选择操作：");
            System.out.println("1. 上传文件");
            System.out.println("2. 检查BMP隐写信息");
            System.out.println("3. 退出");
            
            String choice = scanner.nextLine();
            switch (choice) {
                case "1":
                    System.out.println("请输入文件路径：");
                    String filePath = scanner.nextLine();
                    uploadFile(filePath);
                    break;
                case "2":
                    System.out.println("请输入BMP文件路径：");
                    String bmpPath = scanner.nextLine();
                    checkSteganography(bmpPath);
                    break;
                case "3":
                    running = false;
                    break;
                default:
                    System.out.println("无效的选择");
            }
        }
    }

    private void uploadFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                logger.error("文件不存在: {}", filePath);
                return;
            }

            // 如果是 BMP，询问是否写入隐藏信息
            if (filePath.toLowerCase().endsWith(".bmp")) {
                Scanner scanner = new Scanner(System.in);
                System.out.println("是否在 BMP 中嵌入隐藏信息？(y/n)");
                String answer = scanner.nextLine().trim().toLowerCase();
                if ("y".equals(answer) || "yes".equals(answer)) {
                    System.out.println("请输入要隐藏的文本：");
                    String secret = scanner.nextLine();
                    // 调用隐写写入
                    try {
                        LSBSteganography.hideMessage(filePath, secret);
                        logger.info("已在 BMP 中写入隐藏信息");
                    } catch (Exception ex) {
                        logger.error("写入隐藏信息失败: ", ex);
                        System.out.println("写入隐藏信息失败，继续上传原图。");
                    }
                }
            }

            byte[] fileContent = Files.readAllBytes(path);
            String encodedContent = Base64.encodeBase64String(fileContent);
            
            out.println("UPLOAD:" + path.getFileName());
            out.println(encodedContent);
            out.println("END_UPLOAD");
            
            logger.info("文件上传完成，等待服务器响应");
        } catch (IOException e) {
            logger.error("文件上传失败: ", e);
        }
    }

    private void checkSteganography(String bmpPath) {
        try {
            Path path = Paths.get(bmpPath);
            if (!Files.exists(path)) {
                logger.error("文件不存在: {}", bmpPath);
                return;
            }

            if (!bmpPath.toLowerCase().endsWith(".bmp")) {
                logger.error("不是BMP文件");
                return;
            }

            // 调用LSB隐写检测
            String message = LSBSteganography.extractMessage(bmpPath);
            if (message != null) {
                System.out.println("发现隐藏信息：" + message);
                logger.info("成功提取隐藏信息");
            } else {
                System.out.println("未发现隐藏信息");
                logger.info("未检测到隐藏信息");
            }
        } catch (Exception e) {
            logger.error("检查隐写信息失败: ", e);
        }
    }

    private void handleServerResponses() {
        try {
            String response;
            while (running && (response = in.readLine()) != null) {
                if (response.equals("UPLOAD_SUCCESS:STEGANOGRAPHY")) {
                    logger.info("文件上传成功，服务器已进行隐写处理");
                } else {
                    logger.info("服务器响应: {}", response);
                }
            }
        } catch (IOException e) {
            logger.error("接收服务器响应异常: ", e);
        }
    }

    private void shutdown() {
        running = false;
        executorService.shutdown();
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logger.error("关闭连接失败: ", e);
        }
        logger.info("客户端已关闭");
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.start();

    }
} 