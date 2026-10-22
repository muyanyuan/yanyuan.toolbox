package com.yanyuan.toolbox.shizuku;

interface IShizukuFileService {
    String list(String path, int maxEntries);
    String read(String path, int maxBytes);
    boolean exists(String path);
    String write(String path, String content);
}
