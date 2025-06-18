package com.cryptalink.server;

/**
 * 服务器事件处理器接口
 */
public interface ServerEventHandler {
    /**
     * 当客户端连接时调用
     * @param clientId 客户端标识符
     */
    void onClientConnected(String clientId);
    
    /**
     * 当客户端断开连接时调用
     * @param clientId 客户端标识符
     */
    void onClientDisconnected(String clientId);
    
    /**
     * 当收到文件时调用
     * @param filename 文件名
     */
    void onFileReceived(String filename);
    
    /**
     * 当发生错误时调用
     * @param error 错误信息
     */
    void onError(String error);
}