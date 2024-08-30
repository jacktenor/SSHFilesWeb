package com.sativa.sshfilesweb;

import com.jcraft.jsch.SftpProgressMonitor;

public class FileProgressMonitor implements SftpProgressMonitor {
    private long transferred = 0;
    private long total = 0;

    public FileProgressMonitor(long total) {
        this.total = total;
    }

    @Override
    public void init(int op, String src, String dest, long max) {
        this.transferred = 0;
        System.out.println("Starting transfer: " + src + " to " + dest + " (Total: " + total + " bytes)");
    }

    @Override
    public boolean count(long count) {
        this.transferred += count;
        int progress = (int) ((transferred * 100) / total);
        System.out.println("Progress: " + progress + "% (Transferred: " + transferred + " bytes, Total: " + total + " bytes)");
        ProgressWebSocketHandler.sendProgress(progress); // Send progress to WebSocket
        return true;
    }

    @Override
    public void end() {
        System.out.println("Transfer completed. Total bytes transferred: " + transferred);
        ProgressWebSocketHandler.sendProgress(100); // Ensure progress is marked as 100% at the end
    }
}