package com.cryptalink.client;

import com.cryptalink.common.VersionManager;
import com.cryptalink.server.LSBSteganography;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.imageio.ImageIO;

public class ClientGUI extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(ClientGUI.class);
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8888;

    // 服务器连接相关组件
    private JTextField serverHostField;
    private JTextField serverPortField;
    private JButton connectButton;
    private JButton disconnectButton;
    private JLabel connectionStatusLabel;
    private JLabel versionLabel;

    // 图像隐写相关组件
    private JTextField messageField;
    private JTextArea stegoOutputArea;
    private JLabel imageLabel;
    private File selectedImageFile;
    private JTextField seedField;

    // 文件传输相关组件
    private JTextArea transferLogArea;
    private JProgressBar progressBar;
    private JButton uploadButton;

    // 网络连接相关
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected;
    private SimpleDateFormat dateFormat;
    private final VersionManager versionManager;

    public ClientGUI() {
        setTitle("CryptaLink 客户端");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        dateFormat = new SimpleDateFormat("HH:mm:ss");
        versionManager = VersionManager.getInstance();

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 创建顶部服务器连接面板
        JPanel serverPanel = createServerPanel();
        mainPanel.add(serverPanel, BorderLayout.NORTH);

        // 创建选项卡面板
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("图像隐写", createSteganographyPanel());
        tabbedPane.addTab("文件传输", createFileTransferPanel());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        add(mainPanel);

        // 设置窗口大小和位置
        setSize(1000, 800);
        setLocationRelativeTo(null);

        // 添加窗口关闭事件处理
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                disconnect();
            }
        });
    }

    private JPanel createServerPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(BorderFactory.createTitledBorder("服务器连接"));

        // 服务器地址输入
        JLabel hostLabel = new JLabel("服务器地址:");
        serverHostField = new JTextField(SERVER_HOST, 15);

        // 服务器端口输入
        JLabel portLabel = new JLabel("端口:");
        serverPortField = new JTextField(String.valueOf(SERVER_PORT), 5);

        // 连接按钮
        connectButton = new JButton("连接");
        disconnectButton = new JButton("断开");
        disconnectButton.setEnabled(false);

        // 连接状态
        connectionStatusLabel = new JLabel("未连接");
        connectionStatusLabel.setForeground(Color.RED);

        // 版本信息
        versionLabel = new JLabel("版本: " + versionManager.getVersion());

        connectButton.addActionListener(e -> connect());
        disconnectButton.addActionListener(e -> disconnect());

        panel.add(hostLabel);
        panel.add(serverHostField);
        panel.add(portLabel);
        panel.add(serverPortField);
        panel.add(connectButton);
        panel.add(disconnectButton);
        panel.add(connectionStatusLabel);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(versionLabel);

        return panel;
    }

    private JPanel createSteganographyPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // 创建左侧图像预览区域
        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(BorderFactory.createTitledBorder("图像预览"));
        leftPanel.setPreferredSize(new Dimension(500, 0));

        // 图像预览区域
        imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        JScrollPane imageScrollPane = new JScrollPane(imageLabel);
        leftPanel.add(imageScrollPane, BorderLayout.CENTER);

        // 图像操作按钮
        JPanel imageButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton selectButton = new JButton("选择图片");
        selectButton.addActionListener(e -> selectImage());
        imageButtonPanel.add(selectButton);
        leftPanel.add(imageButtonPanel, BorderLayout.SOUTH);

        // 创建右侧控制面板
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 种子输入框
        JLabel seedLabel = new JLabel("种子值:");
        seedField = new JTextField(20);
        seedField.setMaximumSize(new Dimension(Integer.MAX_VALUE, seedField.getPreferredSize().height));

        // 操作按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton extractButton = new JButton("提取消息");
        extractButton.addActionListener(e -> extractMessage());
        buttonPanel.add(extractButton);

        // 输出区域
        JLabel outputLabel = new JLabel("输出:");
        stegoOutputArea = new JTextArea(8, 20);
        stegoOutputArea.setEditable(false);
        stegoOutputArea.setLineWrap(true);
        stegoOutputArea.setWrapStyleWord(true);
        JScrollPane outputScrollPane = new JScrollPane(stegoOutputArea);

        // 添加组件到右侧面板
        rightPanel.add(seedLabel);
        rightPanel.add(Box.createVerticalStrut(5));
        rightPanel.add(seedField);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(buttonPanel);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(outputLabel);
        rightPanel.add(Box.createVerticalStrut(5));
        rightPanel.add(outputScrollPane);

        // 将左右面板添加到主面板
        panel.add(leftPanel, BorderLayout.CENTER);
        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    private JPanel createFileTransferPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // 创建左侧上传面板
        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(BorderFactory.createTitledBorder("文件上传"));

        // 文件操作按钮
        JPanel fileButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        uploadButton = new JButton("上传文件");
        uploadButton.setEnabled(false);
        uploadButton.addActionListener(e -> uploadFile());
        fileButtonPanel.add(uploadButton);
        leftPanel.add(fileButtonPanel, BorderLayout.SOUTH);

        // 创建右侧传输日志面板
        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBorder(BorderFactory.createTitledBorder("传输日志"));

        // 传输日志区域
        transferLogArea = new JTextArea();
        transferLogArea.setEditable(false);
        transferLogArea.setLineWrap(true);
        transferLogArea.setWrapStyleWord(true);
        JScrollPane logScrollPane = new JScrollPane(transferLogArea);
        rightPanel.add(logScrollPane, BorderLayout.CENTER);

        // 进度条
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        rightPanel.add(progressBar, BorderLayout.SOUTH);

        // 将左右面板添加到主面板
        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(rightPanel, BorderLayout.CENTER);

        return panel;
    }

    private void connect() {
        String host = serverHostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(serverPortField.getText().trim());
        } catch (NumberFormatException e) {
            showError("端口号必须是数字");
            return;
        }

        // 禁用连接按钮，避免重复点击
        connectButton.setEnabled(false);
        connectionStatusLabel.setText("正在连接...");

        // 在后台线程中进行连接操作
        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                connected = true;

                // 启动响应处理线程
                startResponseHandler();

                // 发送版本检查请求
                out.println("VERSION_CHECK");

                // 更新UI状态
                SwingUtilities.invokeLater(() -> {
                    disconnectButton.setEnabled(true);
                    serverHostField.setEnabled(false);
                    serverPortField.setEnabled(false);
                    connectionStatusLabel.setText("已连接");
                    connectionStatusLabel.setForeground(Color.GREEN);
                    uploadButton.setEnabled(true);
                    logTransfer("已连接到服务器 " + host + ":" + port);
                });

            } catch (IOException e) {
                logger.error("连接服务器失败", e);
                SwingUtilities.invokeLater(() -> {
                    showError("连接服务器失败: " + e.getMessage());
                    connectButton.setEnabled(true);
                    connectionStatusLabel.setText("未连接");
                    connectionStatusLabel.setForeground(Color.RED);
                });
            }
        }).start();
    }

    private void disconnect() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                logger.error("关闭socket连接失败", e);
            }
        }

        connected = false;
        socket = null;
        out = null;
        in = null;

        // 更新UI状态
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        serverHostField.setEnabled(true);
        serverPortField.setEnabled(true);
        uploadButton.setEnabled(false);
        connectionStatusLabel.setText("未连接");
        connectionStatusLabel.setForeground(Color.RED);

        logTransfer("已断开连接");
    }

    private void startResponseHandler() {
        Thread responseThread = new Thread(() -> {
            try {
                String response;
                while (connected && (response = in.readLine()) != null) {
                    final String finalResponse = response;
                    SwingUtilities.invokeLater(() -> {
                        if (finalResponse.startsWith("VERSION:")) {
                            String serverVersion = finalResponse.substring(8);
                            if (versionManager.isNewerVersion(serverVersion)) {
                                int choice = JOptionPane.showConfirmDialog(
                                    this,
                                    "发现新版本 " + serverVersion + "，是否更新？",
                                    "版本更新",
                                    JOptionPane.YES_NO_OPTION
                                );
                                
                                if (choice == JOptionPane.YES_OPTION) {
                                    // 获取更新链接
                                    out.println("GET_UPDATE_URL");
                                }
                            }
                        } else if (finalResponse.startsWith("UPDATE_URL:")) {
                            String url = finalResponse.substring(11);
                            downloadAndUpdate(url);
                        } else if (finalResponse.startsWith("UPLOAD_SUCCESS")) {
                            logTransfer("文件上传成功");
                        } else if (finalResponse.startsWith("UPLOAD_FAILED")) {
                            showError("上传失败: " + finalResponse.substring("UPLOAD_FAILED:".length()));
                        }
                    });
                }
            } catch (IOException e) {
                if (connected) {
                    logger.error("读取服务器响应时发生错误", e);
                    SwingUtilities.invokeLater(() -> {
                        showError("与服务器的连接已断开: " + e.getMessage());
                        disconnect();
                    });
                }
            }
        });
        responseThread.setDaemon(true);
        responseThread.start();
    }

    private void selectImage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("BMP Images", "bmp"));
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedImageFile = fileChooser.getSelectedFile();
            
            // 显示图片预览
            displayImage(selectedImageFile);
            
            // 更新状态
            logStego("已选择图片: " + selectedImageFile.getName());
        }
    }

    private void uploadFile() {
        if (!connected || socket == null) {
            showError("未连接到服务器");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                // 如果是BMP文件，询问是否写入隐藏信息
                if (file.getName().toLowerCase().endsWith(".bmp")) {
                    int choice = JOptionPane.showConfirmDialog(
                        this,
                        "是否在BMP中嵌入隐藏信息？",
                        "隐写选项",
                        JOptionPane.YES_NO_OPTION
                    );
                    
                    if (choice == JOptionPane.YES_OPTION) {
                        String secret = JOptionPane.showInputDialog(this, "请输入要隐藏的文本：");
                        if (secret != null && !secret.trim().isEmpty()) {
                            try {
                                // 生成随机种子
                                long seed = LSBSteganography.generateRandomSeed();
                                LSBSteganography.hideMessage(file.getAbsolutePath(), secret, seed);
                                logTransfer("已在BMP中写入隐藏信息，种子值为: " + seed + "（请务必保存此种子值，读取时需要）");
                                
                                // 读取文件内容并进行Base64编码
                                byte[] fileContent = Files.readAllBytes(file.toPath());
                                String encodedContent = Base64.encodeBase64String(fileContent);
                                
                                // 发送上传请求
                                out.println("UPLOAD:" + file.getName());
                                out.println(encodedContent);
                                out.println("END_UPLOAD");
                                out.flush();
                                
                                logTransfer("文件 '" + file.getName() + "' 上传完成，等待服务器响应");
                            } catch (Exception ex) {
                                logger.error("写入隐藏信息失败", ex);
                                showError("写入隐藏信息失败: " + ex.getMessage());
                            }
                        }
                    } else {
                        // 直接上传原始文件
                        uploadOriginalFile(file);
                    }
                } else {
                    // 非BMP文件直接上传
                    uploadOriginalFile(file);
                }
            } catch (IOException e) {
                logger.error("上传文件失败", e);
                showError("上传文件失败: " + e.getMessage());
            }
        }
    }

    private void uploadOriginalFile(File file) throws IOException {
        byte[] fileContent = Files.readAllBytes(file.toPath());
        String encodedContent = Base64.encodeBase64String(fileContent);
        
        out.println("UPLOAD:" + file.getName());
        out.println(encodedContent);
        out.println("END_UPLOAD");
        out.flush();
        
        logTransfer("文件 '" + file.getName() + "' 上传完成，等待服务器响应");
    }

    private void extractMessage() {
        if (selectedImageFile == null) {
            showError("请先选择一个图片文件");
            return;
        }

        try {
            // 获取种子值
            long seed;
            String seedText = seedField.getText().trim();
            if (seedText.isEmpty()) {
                showError("请输入种子值");
                return;
            }
            
            try {
                seed = Long.parseLong(seedText);
            } catch (NumberFormatException e) {
                showError("种子值必须是数字");
                return;
            }
            
            // 提取消息
            String message = LSBSteganography.extractMessage(selectedImageFile.getAbsolutePath(), seed);
            if (message != null) {
                stegoOutputArea.setText("提取到的消息: " + message);
                logStego("成功提取隐藏消息");
            } else {
                showError("未找到隐藏消息或种子值错误");
            }
        } catch (Exception e) {
            logger.error("提取消息失败", e);
            showError("提取消息失败: " + e.getMessage());
        }
    }

    private void hideMessage() {
        showError("请使用文件上传功能来添加隐写信息");
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "错误", JOptionPane.ERROR_MESSAGE);
    }

    private void logStego(String message) {
        String timestamp = dateFormat.format(new Date());
        String logMessage = String.format("[%s] %s", timestamp, message);
        stegoOutputArea.append(logMessage + "\n");
        stegoOutputArea.setCaretPosition(stegoOutputArea.getDocument().getLength());
        logger.info(message);
    }

    private void logTransfer(String message) {
        String timestamp = dateFormat.format(new Date());
        String logMessage = String.format("[%s] %s", timestamp, message);
        transferLogArea.append(logMessage + "\n");
        transferLogArea.setCaretPosition(transferLogArea.getDocument().getLength());
        logger.info(message);
    }

    private void downloadAndUpdate(String updateUrl) {
        try {
            logTransfer("开始下载新版本...");
            
            // 下载新版本
            URL url = new URL(updateUrl);
            Path tempFile = Files.createTempFile("new-client-", ".jar");
            try (InputStream in = url.openStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // 获取当前JAR文件路径
            String currentJarPath = new File(ClientGUI.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getPath();
            Path currentJar = Paths.get(currentJarPath);
            
            // 创建更新脚本
            Path script = Files.createTempFile("update-", ".bat");
            try (BufferedWriter writer = Files.newBufferedWriter(script)) {
                writer.write("@echo off\n");
                writer.write("timeout /t 2 /nobreak > nul\n");  // 等待2秒确保当前程序完全退出
                writer.write("copy /Y \"" + tempFile + "\" \"" + currentJar + "\"\n");
                writer.write("start javaw -jar \"" + currentJar + "\"\n");
                writer.write("del \"" + script + "\"\n");  // 删除脚本自身
            }
            
            // 执行更新脚本
            logTransfer("准备重启新版本...");
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", script.toString());
            pb.start();
            
            // 关闭当前程序
            System.exit(0);
        } catch (Exception e) {
            logger.error("更新失败", e);
            showError("更新失败: " + e.getMessage());
        }
    }

    private void displayImage(File imageFile) {
        try {
            // 读取图片
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
                throw new IOException("无法读取图片文件");
            }

            // 计算缩放比例以适应预览区域
            double scale = Math.min(
                450.0 / img.getWidth(),
                450.0 / img.getHeight()
            );

            // 如果图片太大，进行缩放
            int width = (int) (img.getWidth() * scale);
            int height = (int) (img.getHeight() * scale);

            // 创建缩略图
            Image thumbnail = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            ImageIcon icon = new ImageIcon(thumbnail);

            // 更新预览
            imageLabel.setIcon(icon);
            imageLabel.setText("");  // 清除可能存在的提示文本
            
            // 重新验证并重绘
            imageLabel.revalidate();
            imageLabel.repaint();

        } catch (IOException e) {
            logger.error("显示图片预览失败", e);
            imageLabel.setIcon(null);
            imageLabel.setText("图片预览失败: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            logger.error("设置系统外观失败", e);
        }

        SwingUtilities.invokeLater(() -> {
            new ClientGUI().setVisible(true);
        });
    }
}