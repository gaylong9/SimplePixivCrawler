package org.example;

import java.util.concurrent.ThreadLocalRandom;

public class Main {
    public static void main(String[] args) {
        // 爬取图片存至本地目录
        String downloadDir = "downloadDir";
        // pixiv 账户
        String pixivId = "pixivId";
        // pixiv 密码
        String pixivPwd = "pixivPwd";
        // 大小超过阈值(10MB)的图片不下载
        int threshold = 10 * 1024 * 1024;
        // 上传至服务器目录
        String uploadDir = "uploadDir";
        // 服务器信息
        String host = "host";
        int port = 22;
        String user = "user";
        String password = "password";

        // 爬取图片
        Crawler crawler = new Crawler(downloadDir, pixivId, pixivPwd, threshold);
        crawler.login();
        crawler.downloadMonthlyRanking();
        // crawler.downloadOnePicture("70278071", 5);

        // 上传至服务器
        SftpService sftpService = new SftpService();
        SftpAuthority sftpAuthority = new SftpAuthority(host, port, user, password);

        sftpService.createChannel(sftpAuthority);
        sftpService.uploadFilesFromSrcDir(sftpAuthority, downloadDir, uploadDir, true);
        sftpService.closeChannel();

    }
}
