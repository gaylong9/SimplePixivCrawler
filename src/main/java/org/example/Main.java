package org.example;

import java.util.concurrent.ThreadLocalRandom;

public class Main {
    public static void main(String[] args) {
        // 爬取图片
//        Crawler crawler = new Crawler();
//        crawler.downloadAllUserIllusts();
//        crawler.downloadMaleRanking();
//        crawler.downloadMonthlyRanking();
        // crawler.downloadOneIllustration("70278071", 5);

        // 上传至服务器
        SftpService sftpService = new SftpService();
        sftpService.uploadFilesFromSrcDir();
        sftpService.closeChannel();

    }
}
