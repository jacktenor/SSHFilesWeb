package com.sativa.sshfilesweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.sativa.sshfilesweb")
public class SSHFilesWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(SSHFilesWebApplication.class, args);
    }
}
