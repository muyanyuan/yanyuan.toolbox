package com.yanyuan.toolbox.shizuku;

interface IShizukuTerminalService {
    String execute(String executionId, String program, in String[] args, String cwd, int timeoutMs, int maxOutputBytes);
    void cancel(String executionId);
    String openSession(String sessionId, String program, in String[] args, String cwd, int rows, int cols);
    byte[] readSession(String sessionId, int maxBytes);
    int writeSession(String sessionId, in byte[] data);
    int resizeSession(String sessionId, int rows, int cols);
    int signalSession(String sessionId, int signal);
    void closeSession(String sessionId);
}
