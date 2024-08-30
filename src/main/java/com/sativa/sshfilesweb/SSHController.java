package com.sativa.sshfilesweb;

import com.jcraft.jsch.*;
import jakarta.annotation.PostConstruct;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@CrossOrigin
@RestController
@RequestMapping("/api/ssh")
public class SSHController {

    private Session session;
    private ChannelSftp sftpChannel;
    private final DatabaseManager databaseManager;
    private final JobScheduler jobScheduler;

    @Autowired
    private ApplicationContext applicationContext;

    public SSHController(DatabaseManager databaseManager, JobScheduler jobScheduler) {
        this.databaseManager = databaseManager;
        this.jobScheduler = jobScheduler;
    }

    @PostConstruct
    public void init() {
        // Initialization code if needed
    }

    @PostMapping("/download")
public ResponseEntity<ByteArrayResource> downloadFile(@RequestParam String remoteFilePath, HttpSession httpSession) {
    try {
        Session sshSession = (Session) httpSession.getAttribute("sshSession");
        if (sshSession == null || !sshSession.isConnected()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        ChannelSftp sftpChannel = (ChannelSftp) sshSession.openChannel("sftp");
        sftpChannel.connect();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        SftpATTRS attrs = sftpChannel.lstat(remoteFilePath);
        long fileSize = attrs.getSize(); // Get file size
        sftpChannel.get(remoteFilePath, outputStream, new FileProgressMonitor(fileSize));
        byte[] data = outputStream.toByteArray();
        ByteArrayResource resource = new ByteArrayResource(data);

        sftpChannel.disconnect();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + new File(remoteFilePath).getName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(data.length)
                .body(resource);
    } catch (Exception e) {
        e.printStackTrace();  // Add logging
        return ResponseEntity.status(500).body(null);
    }
}

    @PostMapping("/connect")
    public ResponseEntity<?> connect(@RequestParam String remoteIP, @RequestParam String username,
                                     @RequestParam String password, @RequestParam int port, HttpSession httpSession) {
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, remoteIP, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            Channel channel = session.openChannel("sftp");
            channel.connect();
            sftpChannel = (ChannelSftp) channel;

            String homeDirectory = sftpChannel.getHome();
            Vector<ChannelSftp.LsEntry> fileList = sftpChannel.ls(homeDirectory);
            List<FileInfo> fileInfos = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : fileList) {
                String name = entry.getFilename();
                if (!name.equals(".") && !name.equals("..")) {
                    String type = entry.getAttrs().isDir() ? "directory" : "file";
                    fileInfos.add(new FileInfo(name, type));

                }
            }
            
            httpSession.setAttribute("sshSession", session);

            // Removed the browser-launching code from here
            return ResponseEntity.ok(new ConnectResponse("Connected to remote host!", fileInfos, homeDirectory));
        } catch (Exception e) {
            e.printStackTrace();  // Add logging
            return ResponseEntity.status(500).body("Connection failed: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> listFiles(@RequestParam String directory, HttpSession httpSession) throws JSchException {
        try {
            Session sshSession = (Session) httpSession.getAttribute("sshSession");
            if (sshSession == null || !sshSession.isConnected()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No active SSH session.");
            }

            ChannelSftp sftpChannel = (ChannelSftp) sshSession.openChannel("sftp");
            sftpChannel.connect();

            Vector<ChannelSftp.LsEntry> fileList = sftpChannel.ls(directory);
            List<FileInfo> fileInfos = new ArrayList<>();
            
            // Separate the entries into categories
            List<FileInfo> dots = new ArrayList<>();
            List<FileInfo> directories = new ArrayList<>();
            List<FileInfo> files = new ArrayList<>();
            List<FileInfo> hiddenFiles = new ArrayList<>();
            
            for (ChannelSftp.LsEntry entry : fileList) {
                String name = entry.getFilename();
                String type = entry.getAttrs().isDir() ? "directory" : "file";

                if (name.equals(".") || name.equals("..")) {
                    dots.add(new FileInfo(name, type));
                } else if (entry.getAttrs().isDir()) {
                    directories.add(new FileInfo(name, type));
                } else if (name.startsWith(".")) {
                    hiddenFiles.add(new FileInfo(name, type));
                } else {
                    files.add(new FileInfo(name, type));
                }
            }

            // Sort the lists
            Comparator<FileInfo> alphabeticComparator = Comparator.comparing(FileInfo::getName);
            directories.sort(alphabeticComparator);
            files.sort(alphabeticComparator);
            hiddenFiles.sort(alphabeticComparator);

            // Combine lists in the correct order
            fileInfos.addAll(dots);
            fileInfos.addAll(directories);
            fileInfos.addAll(files);
            fileInfos.addAll(hiddenFiles);
            
            sftpChannel.disconnect();
            return ResponseEntity.ok(fileInfos);
        } catch (SftpException e) {
            e.printStackTrace();  // Add logging
            return ResponseEntity.status(500).body("Failed to list files: " + e.getMessage());
        }
    }
    
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, String>> disconnect(HttpSession session) {
        Session sshSession = (Session) session.getAttribute("sshSession");
        if (sshSession != null && sshSession.isConnected()) {
            sshSession.disconnect();
            session.removeAttribute("sshSession");
            // Call the method to shut down the backend
            shutdownBackend();

            return ResponseEntity.ok(Map.of("message", "Disconnected and backend shut down successfully."));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "No active session to disconnect."));
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam MultipartFile file) {
        try {
            File tempFile = File.createTempFile("upload-", file.getOriginalFilename());
            file.transferTo(tempFile);

            long fileSize = tempFile.length(); // Get the file size

            try (FileInputStream fis = new FileInputStream(tempFile)) {
                sftpChannel.put(fis, file.getOriginalFilename(), new FileProgressMonitor(fileSize), ChannelSftp.OVERWRITE);
            }

            tempFile.delete();
            return ResponseEntity.ok("File uploaded successfully!");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("File upload failed: " + e.getMessage());
        }
    }
    
    @PostMapping("/download-directory")
    public ResponseEntity<ByteArrayResource> downloadDirectory(@RequestParam String directory, HttpSession httpSession) {
        try {
            Session sshSession = (Session) httpSession.getAttribute("sshSession");
            if (sshSession == null || !sshSession.isConnected()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }

            ChannelSftp sftpChannel = (ChannelSftp) sshSession.openChannel("sftp");
            sftpChannel.connect();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            long totalSize = calculateDirectorySize(directory); // Calculate total directory size
            FileProgressMonitor monitor = new FileProgressMonitor(totalSize);
            compressDirectory(directory, baos, monitor);
            byte[] data = baos.toByteArray();
            ByteArrayResource resource = new ByteArrayResource(data);

            sftpChannel.disconnect();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + new File(directory).getName() + ".zip\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(data.length)
                    .body(resource);
        } catch (Exception e) {
            e.printStackTrace();  // Add logging
            return ResponseEntity.status(500).body(null);
        }
    }

    private long calculateDirectorySize(String directory) throws SftpException {
        long totalSize = 0;
        Vector<ChannelSftp.LsEntry> fileList = sftpChannel.ls(directory);
        for (ChannelSftp.LsEntry entry : fileList) {
            String filePath = directory + "/" + entry.getFilename();
            if (entry.getAttrs().isDir()) {
                if (!entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                    totalSize += calculateDirectorySize(filePath);
                }
            } else {
                totalSize += entry.getAttrs().getSize();
            }
        }
        return totalSize;
    }

    private void compressDirectory(String directoryPath, ByteArrayOutputStream baos, FileProgressMonitor monitor) throws IOException, SftpException {
        try (ZipOutputStream zipOut = new ZipOutputStream(baos)) {
            compressDirectoryRecursive(directoryPath, directoryPath, zipOut, monitor);
        }
    }

    private void compressDirectoryRecursive(String rootDir, String sourceDir, ZipOutputStream zipOut, FileProgressMonitor monitor) throws IOException, SftpException {
        Vector<ChannelSftp.LsEntry> fileList = sftpChannel.ls(sourceDir);
        for (ChannelSftp.LsEntry entry : fileList) {
            String filePath = sourceDir + "/" + entry.getFilename();
            if (entry.getAttrs().isDir()) {
                if (!entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                    compressDirectoryRecursive(rootDir, filePath, zipOut, monitor);
                }
            } else {
                try (InputStream fis = sftpChannel.get(filePath)) {
                    String zipEntryName = filePath.substring(rootDir.length() + 1);
                    zipOut.putNextEntry(new ZipEntry(zipEntryName));
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zipOut.write(buffer, 0, length);
                        monitor.count(length); // Update progress for each byte read
                    }
                    zipOut.closeEntry();
                }
            }
        }
    }
    
    private void shutdownBackend() {
        // Shutdown database connection
        databaseManager.shutdown();

        // Shutdown job scheduler
        jobScheduler.shutdown();

        // Optionally shut down the application context if required
        shutDownApplication();
    }

    private void shutDownApplication() {
        System.out.println("Shutting down application...");
        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(exitCode);
    }

    static class FileInfo {
        private String name;
        private String type;

        public FileInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
    }

    static class ConnectResponse {
        private String message;
        private List<FileInfo> fileList;
        private String currentDirectory;

        public ConnectResponse(String message, List<FileInfo> fileList, String currentDirectory) {
            this.message = message;
            this.fileList = fileList;
            this.currentDirectory = currentDirectory;
        }

        public String getMessage() {
            return message;
        }

        public List<FileInfo> getFileList() {
            return fileList;
        }

        public String getCurrentDirectory() {
            return currentDirectory;
        }
    }
}