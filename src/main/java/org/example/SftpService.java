package org.example;

import com.jcraft.jsch.*;

import java.io.File;
import java.util.Arrays;

public class SftpService {
    private Session session;
    private Channel channel;
    private ChannelSftp channelSftp;

    void createChannel(SftpAuthority sftpAuthority) {
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(sftpAuthority.getUser(), sftpAuthority.getHost(), sftpAuthority.getPort());
            session.setPassword(sftpAuthority.getPassword());
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

    boolean uploadFilesFromSrcDir(SftpAuthority sftpAuthority, String srcDir, String dstDir, boolean delAfterUpload) {
        // 检查通道是否正常
        if (channelSftp == null) {
            System.out.println("channelSftp is not created");
            return false;
        }

        if (channelSftp.isClosed()) {
            createChannel(sftpAuthority);
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

    }
}
