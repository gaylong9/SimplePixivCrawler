package org.example;

import com.alibaba.fastjson.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.UncheckedIOException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Crawler {
    String UserAgent;
    String session;
    // pixiv 账户
    String id;
    // pixiv 密码
    String password;
    // 图片下载目录
    String filePath;
    // 目录中已有的图片名
    HashSet<String> existFileNames;
    // 大于10MB的图片跳过
    int threshold;
    ArrayList<String> downloadFailedFiles = new ArrayList<>();
    int imgNumOnStart;

    Crawler(String filePath, String id, String password, int threshold) {
        int UAIdx = ThreadLocalRandom.current().nextInt(0, PixivUrl.User_Agents.length);
        this.UserAgent = PixivUrl.User_Agents[UAIdx];
        this.filePath = filePath;
        this.id = id;
        this.password = password;
        this.threshold = threshold;
        File downloadDir = new File(filePath);
        //判断文件目录是否存在
        if (!downloadDir.exists()) {
            if (!downloadDir.mkdir()) {
                System.err.println("download directory is not exist, and mkdir failed");
                return;
            }
        }
        if (!downloadDir.isDirectory()) {
            System.err.println("download path should be directory");
            return;
        }
        String[] existNames = downloadDir.list();
        imgNumOnStart = existNames.length;
        existFileNames = new HashSet<>(List.of(existNames));
        System.out.printf("download directory contains %d imgs\n", imgNumOnStart);
    }

    /**
     * 登录
     * 参考1 https://www.cnblogs.com/fightfordream/p/6421498.html
     * 参考2 https://www.tqwba.com/x_d/jishu/244706.html
     */
    void login() {
        // 先获取postKey
        Document document = null;
        try {
            document = Jsoup.connect(PixivUrl.LOGIN_PAGE_URL)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3")
                    .header("User-Agent", UserAgent)
                    .header("Referer", PixivUrl.LOGIN_PAGE_URL)
                    .proxy("127.0.0.1", 7890)
                    .timeout(10000)
                    .get();
        } catch (IOException e) {
            System.err.println("connect to login page failed");
            e.printStackTrace();
            return;
        }
        String json = Objects.requireNonNull(document.body().getElementById("init-config")).attr("value");
        String postKeyRegex = "(?<=\"pixivAccount.postKey\":\")[\\w]*";
        Matcher matcher = Pattern.compile(postKeyRegex).matcher(json);
        if (!matcher.find()) {
            System.err.println("postKey not found, login failed");
            return;
        }
        String postKey = matcher.group(0);

        // 登录
        Connection.Response response = null;
        try {
            response = Jsoup.connect(PixivUrl.LOGIN_API_URL)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3")
                    .header("User-Agent", UserAgent)
                    .header("Referer", PixivUrl.LOGIN_PAGE_URL)
                    .data("pixiv_id", id)
                    .data("password", password)
                    .data("return_to", "https://www.pixiv.net/")
                    .data("post_key", postKey)
                    .proxy("127.0.0.1", 7890)
                    .ignoreContentType(true)
                    .timeout(10000)
                    .method(Connection.Method.POST)
                    .execute();
        } catch (IOException e) {
            System.err.println("login failed");
            e.printStackTrace();
            return;
        }

//        System.out.println(response.headers());
//        System.out.println(response.body());
//        System.out.println(response.cookies());

        JSONObject jsonObject = JSONObject.parseObject(response.body());
        boolean error = jsonObject.getBooleanValue("error");

        if (!error) {
            System.out.println("login success");
            session = response.cookie("PHPSESSID");
            System.out.println("cookie: " + session);
        } else {
            String errorMessage = jsonObject.getJSONObject("body").get("validation_errors").toString();
            System.err.println("login failed, " + errorMessage);
        }
    }

    /**
     * 下载当月排行榜的所有插画
     * 参考1 https://blog.csdn.net/owaranaiyume/article/details/114667736
     * 参考2 https://blog.csdn.net/weixin_45826022/article/details/109406389
     */
    void downloadMonthlyRanking() {
        if (session == null) {
            login();
        }
        if (session == null) {
            System.err.println("login failed");
            return;
        }

        String illustIdRegex = "(?<=\"illust_id\":)[\\d]*";
        String illustPageCntRegex = "(?<=\"illust_page_count\":\")[\\d]*(?=\")";
        Pattern illustIdPattern = Pattern.compile(illustIdRegex);
        Pattern illustPageCntPattern = Pattern.compile(illustPageCntRegex);

        int curIdx = 1;

        // 周榜有10组*50个作品，共500个，月榜不然，超出上限请求失败
        for (int page = 1; page <= 10; page++) {
            Document document = null;
            try {
                document = Jsoup.connect(PixivUrl.MONTHLY_URL + "&p=" + page + "&format=json")
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        .header("Accept-Encoding", "gzip, deflate, br")
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .header("User-Agent", UserAgent)
                        .header("Referer", "https://www.pixiv.net/ranking.php?mode=weekly&content=illust")
                        .cookie("PHPSESSID", session)
                        .proxy("127.0.0.1", 7890)
                        .ignoreContentType(true)
                        .timeout(10000)
                        .get();
            } catch (IOException e) {
                System.err.println("connect to weekly ranking page failed");
                e.printStackTrace();
                return;
            }
            String json = document.body().text();
            Matcher idMatcher = illustIdPattern.matcher(json);
            Matcher pageCntMatcher = illustPageCntPattern.matcher(json);
            // 每个作品可能有多张图片
            while (idMatcher.find() && pageCntMatcher.find()) {
                System.out.printf("\ndownloading %d...\n", curIdx++);
                downloadOnePicture(idMatcher.group(0), Integer.parseInt(pageCntMatcher.group(0)));
            }
        }
        System.out.printf("download finished, success %d, failed %d\n", existFileNames.size() - imgNumOnStart, downloadFailedFiles.size());
        System.out.println("download failed files:");
        for (String fileName : downloadFailedFiles) {
            System.out.println(fileName);
        }
    }

    /**
     * 下载一个作品
     * @param illustId 作品id
     * @param pageCnt 该作品有几张图
     */
    void downloadOnePicture(String illustId, int pageCnt) {
        if (session == null) {
            login();
        }
        if (session == null) {
            System.err.println("login failed");
            return;
        }

        // 访问作品页，获取原图链接
        String illustSrc = PixivUrl.ARTWORK_URL + illustId;
        Document document = null;
        try {
            document = Jsoup.connect(illustSrc)
                    .header("Accept", "application/*, text/*, */*; q=0.01")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("User-Agent", UserAgent)
                    .header("Referer", PixivUrl.MONTHLY_URL)
                    .cookie("PHPSESSID", session)
                    .proxy("127.0.0.1", 7890)
                    .timeout(10000)
                    .get();
        } catch (IOException e) {
            System.err.println("connect to illustrate page failed");
            e.printStackTrace();
            return;
        }

        // 获取图片链接
        Element meta = document.head().getElementById("meta-preload-data");
        if (meta == null || !meta.hasAttr("content")) {
            System.err.println("illustrate page load failed because has no meta-preload-data");
            return;
        }
//        System.out.println(document.head());
//        System.out.println("html head key-meta attr-content:");
//        System.out.println(meta.attr("content"));

        String imgSrcRegex = "(?<=\"original\":\")[\\w\\./_:-]*(?=\"})";
        Matcher matcher = Pattern.compile(imgSrcRegex).matcher(meta.attr("content"));
        if (!matcher.find()) {
            System.err.println("illustrate page load failed because meta has no original link");
            return;
        }
        String imageSrc = matcher.group(0);
        int suffixStartIdx = imageSrc.lastIndexOf('.');
        String suffix = imageSrc.substring(suffixStartIdx);
        imageSrc = imageSrc.substring(0, imageSrc.length() - suffix.length() - 1);
//        System.out.println("Image source:" + imageSrc);
        // 每个作品可能有多个图片，逐个访问下载
        for (int pageIdx = 0; pageIdx < pageCnt; pageIdx++) {
            String fileName = illustId + "_p" + pageIdx + suffix;
            if (existFileNames.contains(fileName)) {
                System.out.printf("%d/%d already exist, skip\n", pageIdx + 1, pageCnt);
            } else {
                System.out.printf("%d/%d...\n", pageIdx + 1, pageCnt);
                download(illustId, imageSrc + pageIdx + suffix, fileName);
            }
        }
    }

    /**
     * 下载一张图片
     * @param illustId 作品id
     * @param url 图片地址
     * @param fileName 最终保存的文件名
     */
    private void download(String illustId, String url, String fileName) {
        if (session == null) {
            login();
        }
        if (session == null) {
            System.err.println("login failed");
            return;
        }

        // 访问图片页
        Connection.Response response;
        byte[] img;
        try {
            response = Jsoup.connect(url)
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("User-Agent", UserAgent)
                    .header("Referer", PixivUrl.ARTWORK_URL + illustId)
                    .proxy("127.0.0.1", 7890)
                    .ignoreContentType(true)    // return image/jpeg
                    .maxBodySize(Integer.MAX_VALUE)
                    .timeout(10000)
                    .execute();
            img = response.bodyAsBytes();
        } catch (IOException | UncheckedIOException e) {
            System.err.println("download failed, file name: " + fileName);
            downloadFailedFiles.add(fileName);
            e.printStackTrace();
            return;
        }

        if (img.length > threshold) {
            // 大过10MB的图片不下载
            System.out.println("img size " + img.length + "B, bigger than threshold, skip");
            return;
        }
        BufferedOutputStream bos = null;
        FileOutputStream fos = null;
        File file;
        try {
            file = new File(filePath + "\\" + fileName);
            fos =  new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bos.write(img);
            existFileNames.add(fileName);
        } catch (IOException e) {
            e.printStackTrace();
        } finally{
            if(bos != null){
                try {
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if(fos != null){
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {
        Crawler demo = new Crawler("D://File//temp", "id", "password", 100000000);
    }
}
