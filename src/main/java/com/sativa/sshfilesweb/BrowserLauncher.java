package com.sativa.sshfilesweb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class BrowserLauncher {

    @PostConstruct
    public void init() {
        try {            

            // Open the default web browser using xdg-open
            ProcessBuilder processBuilder = new ProcessBuilder("xdg-open", " localhost:8080/index.html");
            Process process = processBuilder.start();
            logBrowserOutput(process);
        } catch (IOException e) {
            e.printStackTrace();  // Add logging
        }
    }

    private void logBrowserOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Browser output: " + line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}