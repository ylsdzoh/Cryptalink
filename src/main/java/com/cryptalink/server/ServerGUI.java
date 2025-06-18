package com.cryptalink.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Vector;

public class ServerGUI extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(ServerGUI.class);
    
    private JTextArea logArea;
    private JList<String> clientList;
    private JList<String> fileList;
    private JButton startButton;
    private JButton stopButton;
    private JLabel statusLabel;
    private DefaultListModel<String> clientListModel;
    private DefaultListModel<String> fileListModel;
    private Server server;
    private SimpleDateFormat dateFormat;
    
    public ServerGUI() {
        setTitle("CryptaLink 服务器");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        // 初始化组件
        dateFormat = new SimpleDateFormat("HH:mm:ss");
        clientListModel = new DefaultListModel<>();
        fileListModel = new DefaultListModel<>();
        
        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 创建左侧面板（客户端列表和文件列表）
        JPanel leftPanel = createLeftPanel();
        mainPanel.add(leftPanel, BorderLayout.WEST);
        
        // 创建中央面板（日志区域）
        JPanel centerPanel = createCenterPanel();
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // 创建顶部控制面板
        JPanel controlPanel = createControlPanel();
        mainPanel.add(controlPanel, BorderLayout.NORTH);
        
        // 创建底部状态栏
        statusLabel = new JLabel("服务器已停止");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // 设置窗口大小和位置
        setSize(800, 600);
        setLocationRelativeTo(null);
        
        // 添加窗口关闭事件处理
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopServer();
            }
        });
        
        // 初始化服务器
        initializeServer();
    }
    
    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 10));
        panel.setPreferredSize(new Dimension(200, 0));
        
        // 客户端列表
        JPanel clientPanel = new JPanel(new BorderLayout());
        clientPanel.setBorder(BorderFactory.createTitledBorder("已连接客户端"));
        clientList = new JList<>(clientListModel);
        clientList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane clientScrollPane = new JScrollPane(clientList);
        clientPanel.add(clientScrollPane);
        
        // 文件列表
        JPanel filePanel = new JPanel(new BorderLayout());
        filePanel.setBorder(BorderFactory.createTitledBorder("已上传文件"));
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane fileScrollPane = new JScrollPane(fileList);
        filePanel.add(fileScrollPane);
        
        panel.add(clientPanel);
        panel.add(filePanel);
        
        return panel;
    }
    
    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("服务器日志"));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(logArea);
        panel.add(scrollPane);
        
        return panel;
    }
    
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        startButton = new JButton("启动服务器");
        stopButton = new JButton("停止服务器");
        stopButton.setEnabled(false);
        
        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());
        
        panel.add(startButton);
        panel.add(stopButton);
        
        return panel;
    }
    
    private void initializeServer() {
        server = new Server();
        
        // 设置服务器事件处理器
        server.setEventHandler(new ServerEventHandler() {
            @Override
            public void onClientConnected(String clientId) {
                SwingUtilities.invokeLater(() -> {
                    clientListModel.addElement(clientId);
                    log("客户端连接: " + clientId);
                });
            }
            
            @Override
            public void onClientDisconnected(String clientId) {
                SwingUtilities.invokeLater(() -> {
                    clientListModel.removeElement(clientId);
                    log("客户端断开连接: " + clientId);
                });
            }
            
            @Override
            public void onFileReceived(String filename) {
                SwingUtilities.invokeLater(() -> {
                    fileListModel.addElement(filename);
                    log("收到文件: " + filename);
                });
            }
            
            @Override
            public void onError(String error) {
                SwingUtilities.invokeLater(() -> {
                    log("错误: " + error);
                });
            }
        });
    }
    
    private void startServer() {
        try {
            server.start();
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            statusLabel.setText("服务器运行中");
            log("服务器已启动");
        } catch (Exception e) {
            logger.error("启动服务器失败", e);
            showError("启动服务器失败: " + e.getMessage());
        }
    }
    
    private void stopServer() {
        try {
            server.stop();
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            statusLabel.setText("服务器已停止");
            log("服务器已停止");
            
            // 清空列表
            clientListModel.clear();
            fileListModel.clear();
        } catch (Exception e) {
            logger.error("停止服务器失败", e);
            showError("停止服务器失败: " + e.getMessage());
        }
    }
    
    private void log(String message) {
        String timestamp = dateFormat.format(new Date());
        logArea.append(String.format("[%s] %s%n", timestamp, message));
        // 滚动到最新的日志
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
    
    private void showError(String message) {
        JOptionPane.showMessageDialog(this,
            message,
            "错误",
            JOptionPane.ERROR_MESSAGE);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // 设置本地系统外观
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                logger.error("设置外观失败", e);
            }
            new ServerGUI().setVisible(true);
        });
    }
}