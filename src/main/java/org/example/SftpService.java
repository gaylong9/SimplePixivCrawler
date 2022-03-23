package org.example;

import com.jcraft.jsch.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class SftpService {
    private Session session;
    private Channel channel;
    private ChannelSftp channelSftp;
    private String host;
    private int port;
    private String user;
    private String password;
    private String ymlFileName = "sftp_cfg.yml";
    private String srcDir, dstDir;
    private boolean delAfterUpload;


    SftpService() {
        // 从resources/sftp_cfg.yml读取配置项
        Yaml yaml = new Yaml();
        File ymlFile = new File(System.getProperty("user.dir") + "/src/main/resources/" + ymlFileName);
        if (!ymlFile.exists()) {
            System.err.println(ymlFileName + " is not exists");
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(ymlFile), StandardCharsets.UTF_8)) {
            Map<String, Object> map = yaml.load(reader);
            host = (String) map.get("host");
            password = (String) map.get("password");
            port = (int) map.get("port");
            user = (String) map.get("user");
            srcDir = (String) map.get("srcDir");
            dstDir = (String) map.get("dstDir");
            delAfterUpload = (boolean) map.get("delAfterUpload");
            System.out.println("sftp cfg: " + host + " " + port + " " + user + " " + password);
            System.out.println(srcDir + "->" + dstDir + ", delAfterUpload " + delAfterUpload);
        } catch (IOException e) {
            System.err.println("load yml failed");
            return;
        }
    }

    void createChannel() {
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, port);
            session.setPassword(password);
            session.setTimeout(10000);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            channel = session.openChannel("sftp");
            channel.connect();
            channelSftp = (ChannelSftp) channel;
        } catch (JSchException e) {
            e.printStackTrace();
        }
    }

    void closeChannel() {
        if (channel != null) {
            channel.disconnect();
        }
        if (session != null) {
            session.disconnect();
        }
    }

    boolean uploadFilesFromSrcDir() {
        // 检查通道是否正常
        if (channelSftp == null) {
            createChannel();
        }
        if (channelSftp == null) {
            System.out.println("channelSftp create failed");
            return false;
        }

        if (channelSftp.isClosed()) {
            createChannel();
        }

        // 检查源目录是否存在
        File src = new File(srcDir);
        if (!src.exists() || !src.isDirectory()) {
            System.out.println("src is not exists or is not dir");
            return false;
        }

        // 检查目标目录是否存在
        try {
            channelSftp.cd(dstDir);
        } catch (SftpException e) {
            e.printStackTrace();
            System.out.println("dst dir is not exist, will create");
            try {
                channelSftp.mkdir(dstDir);
            } catch (SftpException ex) {
                ex.printStackTrace();
                System.out.println("create dst dir failed");
                return false;
            }
        }

        // 将源目录下所有文件发至目标目录
        try {
            File[] files = src.listFiles();
            int sum = files.length;
            for (int i = 0; i < sum; i++) {
                System.out.printf("upload %d/%d...\n", i + 1, sum);
                channelSftp.put(files[i].getAbsolutePath(), dstDir, ChannelSftp.OVERWRITE);
                files[i].delete();
            }
            return true;
        } catch (SftpException e) {
            e.printStackTrace();
            return false;
        }
    }

    void delFilesInSrcDir(String srcDir) {
        // 检查源目录是否存在
        File src = new File(srcDir);
        if (!src.exists() || !src.isDirectory()) {
            System.out.println("src is not exists or is not dir");
            return;
        }
        File[] files = src.listFiles();
        System.out.println(Arrays.toString(files));
        for (File file : files) {
            if (!file.delete()) {
                System.out.println(file.getName() + " del failed");
            }
        }
    }

    public static void main(String[] args) {
        SftpService demo = new SftpService();
//        System.out.println(System.getProperty("user.dir") + "/src/main/resources/");
    }
}
