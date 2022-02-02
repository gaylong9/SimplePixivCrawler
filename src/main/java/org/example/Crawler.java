package org.example;

import com.alibaba.fastjson.JSONObject;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UncheckedIOException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Crawler {
    String UserAgent;
    String session;
    String PHPSESSID;
    // pixiv 账户
    String id;
    // pixiv 密码
    String password;
    // 图片下载目录
    String filePath;
    // 目录中已有的图片名
    HashSet<String> existFileNames;
    // 图片大小大于阈值的的跳过
    int threshold;
    // 作品中图片数大于阈值的跳过
    int pageCntThreshold;
    // 是否下载R18作品
    boolean allowR18;
    ArrayList<String> downloadFailedFiles = new ArrayList<>();
    int imgNumOnStart;
    String ymlFileName = "crawler_cfg.yml";
    String userIllustrationsFileName = "user_illustrations.yml";
    // 记录作者与下载记录
    HashMap<String, String> userIllustrateMap;

    Crawler() {
        // 从resources/crawler_cfg.yml读取配置项
        Yaml yaml = new Yaml();
        File crawlerCfg = new File(System.getProperty("user.dir") + "/src/main/resources/" + ymlFileName);
        if (!crawlerCfg.exists()) {
            System.err.println(ymlFileName + " is not exists");
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(crawlerCfg), StandardCharsets.UTF_8)) {
            Map<String, Object> map = yaml.load(reader);
            int UAIdx = ThreadLocalRandom.current().nextInt(0, PixivUrl.User_Agents.length);
            UserAgent = PixivUrl.User_Agents[UAIdx];
            filePath = (String) map.get("filePath");
            id = (String) map.get("id");
            password = (String) map.get("password");
            threshold = (int) map.get("threshold");
            pageCntThreshold = (int) map.get("pageCntThreshold");
            allowR18 = (boolean) map.get("allowR18");
            PHPSESSID = (String) map.get("PHPSESSID");
        } catch (IOException e) {
            System.err.println("load yml failed");
            return;
        }

        userIllustrateMap = new HashMap<String, String>();
        // 获取用户作品下载记录
        File userIllustrate = new File(System.getProperty("user.dir") + "/src/main/resources/" + userIllustrationsFileName);
        if (!userIllustrate.exists()) {
            System.err.println(userIllustrationsFileName + " is not exists");
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(userIllustrate), StandardCharsets.UTF_8)) {
            Map<String, Object> map = yaml.load(reader);
            Map<?, ?> rawMap = (Map<?, ?>) map.get("userIllustrations");
            for (Object key : rawMap.keySet()) {
                String id = (String) key;
                String illustId = (String) rawMap.get(key);
                if (this.userIllustrateMap.containsKey(id)) {
                    if (illustId.compareTo(this.userIllustrateMap.get(id)) >= 0) {
                        this.userIllustrateMap.put(id, illustId);
                    }
                } else {
                    this.userIllustrateMap.put(id, illustId);
                }
            }
        } catch (IOException e) {
            System.err.println("open user illustrations yml failed");
            e.printStackTrace();
            return;
        }

        // 判断session
        if (PHPSESSID == null || PHPSESSID.equals("")) {
            System.err.println("should set PHPSESSID in crawler_cfg.yml");
            return;
        }

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
    @Deprecated
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

        System.out.println("body");
        System.out.println(response.body());

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
        if (!checkSession()) {
            return;
        }

        String illustIdRegex = "(?<=\"illust_id\":)[\\d]*";
        String illustPageCntRegex = "(?<=\"illust_page_count\":\")[\\d]*(?=\")";
        Pattern illustIdPattern = Pattern.compile(illustIdRegex);
        Pattern illustPageCntPattern = Pattern.compile(illustPageCntRegex);

        int curIdx = 1;

        // 周榜有10组*50个作品，共500个，月榜245个
        for (int page = 1; page <= 5; page++) {
            Document document = null;
            try {
                document = Jsoup.connect(PixivUrl.MONTHLY_RANKING_ILLUST_URL + "&p=" + page + "&format=json")
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        .header("Accept-Encoding", "gzip, deflate, br")
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .header("User-Agent", UserAgent)
                        .header("Referer", PixivUrl.MONTHLY_RANKING_ILLUST_URL)
                        .cookie("PHPSESSID", PHPSESSID)
                        .proxy("127.0.0.1", 7890)
                        .ignoreContentType(true)
                        .timeout(10000)
                        .get();
            } catch (IOException e) {
                System.err.println("connect to monthly ranking page failed");
                e.printStackTrace();
                return;
            }
            String json = document.body().text();
            Matcher idMatcher = illustIdPattern.matcher(json);
            Matcher pageCntMatcher = illustPageCntPattern.matcher(json);
            // 每个作品可能有多张图片
            while (idMatcher.find() && pageCntMatcher.find()) {
                System.out.printf("\ndownloading %d...\n", curIdx++);
                String illustId = idMatcher.group(0);
                int pageCnt = Integer.parseInt(pageCntMatcher.group(0));
                if (pageCnt > pageCntThreshold) {
                    continue;
                }
                downloadOneIllustration(illustId, pageCnt, PixivUrl.MONTHLY_RANKING_ILLUST_URL);
            }
        }
        System.out.printf("download finished, success %d, failed %d\n", existFileNames.size() - imgNumOnStart, downloadFailedFiles.size());
        System.out.println("download failed files:");
        for (String fileName : downloadFailedFiles) {
            System.out.println(fileName);
        }
        imgNumOnStart = existFileNames.size();
        downloadFailedFiles.clear();
    }

    /**
     * 下载受男性欢迎榜单
     */
    void downloadMaleRanking() {
        if (!checkSession()) {
            return;
        }

        String illustIdRegex = "(?<=\"illust_id\":)[\\d]*";
        String illustPageCntRegex = "(?<=\"illust_page_count\":\")[\\d]*(?=\")";
        Pattern illustIdPattern = Pattern.compile(illustIdRegex);
        Pattern illustPageCntPattern = Pattern.compile(illustPageCntRegex);

        int curIdx = 1;

        // 虽然有500个，但后一半质量不太高，故page应10改5
        for (int page = 1; page <= 5; page++) {
            Document document = null;
            try {
                document = Jsoup.connect(PixivUrl.MALE_RANKING + "&p=" + page + "&format=json")
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        .header("Accept-Encoding", "gzip, deflate, br")
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .header("User-Agent", UserAgent)
                        .header("Referer", PixivUrl.MALE_RANKING)
                        .cookie("PHPSESSID", PHPSESSID)
                        .proxy("127.0.0.1", 7890)
                        .ignoreContentType(true)
                        .timeout(10000)
                        .get();
            } catch (IOException e) {
                System.err.println("connect to male ranking page failed");
                e.printStackTrace();
                return;
            }
            String json = document.body().text();
            Matcher idMatcher = illustIdPattern.matcher(json);
            Matcher pageCntMatcher = illustPageCntPattern.matcher(json);
            // 每个作品可能有多张图片
            while (idMatcher.find() && pageCntMatcher.find()) {
                System.out.printf("\ndownloading %d...\n", curIdx++);
                String illustId = idMatcher.group(0);
                int pageCnt = Integer.parseInt(pageCntMatcher.group(0));
                if (pageCnt > pageCntThreshold) {
                    continue;
                }
                downloadOneIllustration(illustId, pageCnt, PixivUrl.MALE_RANKING);
            }
        }
        System.out.printf("download finished, success %d, failed %d\n", existFileNames.size() - imgNumOnStart, downloadFailedFiles.size());
        System.out.println("download failed files:");
        for (String fileName : downloadFailedFiles) {
            System.out.println(fileName);
        }
        imgNumOnStart = existFileNames.size();
        downloadFailedFiles.clear();
    }

    /**
     * 下载yml中所有作者的新作品
     */
    void downloadAllUserIllusts() {
        int curUserIdx = 1, userSum = userIllustrateMap.size();
        System.out.println(userIllustrateMap.size() + " users in yml");
        for (Map.Entry<String, String> entry : userIllustrateMap.entrySet()) {
            System.out.printf("downloading user %d/%d(%s)...\n", curUserIdx++, userSum, entry.getKey());
            String newestIllustId = downloadUserIllustrations(entry.getKey(), entry.getValue());
            entry.setValue(newestIllustId);
        }
        // 数据写回
        Yaml yaml = new Yaml();
        File userIllustrate = new File(System.getProperty("user.dir") + "/src/main/resources/" + userIllustrationsFileName);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(userIllustrate, false), StandardCharsets.UTF_8)) {
            HashMap<String, Object> map = new HashMap<>();
            map.put("userIllustrations", userIllustrateMap);
            yaml.dump(map, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 下载某个作者的指定作品以来的作品
     * @param userId 作者id
     * @param illustId 下载该作品之后的作品
     * @return 该作者最新作品id
     */
    String downloadUserIllustrations(String userId, String illustId) {
        if (!checkSession()) {
            return illustId;
        }

        // 获取该作者所有作品id
        Connection.Response response;
        try {
            response = Jsoup.connect(PixivUrl.BASE_URL + "/ajax/user/" + userId + "/profile/all?lang=zh")
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3")
                    .header("User-Agent", UserAgent)
                    .header("Referer", PixivUrl.USER_PAGE + userId + "/profile/all?lang=zh")
                    .cookie("PHPSESSID", PHPSESSID)
                    .proxy("127.0.0.1", 7890)
                    .ignoreContentType(true)
                    .timeout(10000)
                    .method(Connection.Method.GET)
                    .execute();
        } catch (IOException e) {
            System.err.println("connect to user " + userId + " illustrations page failed");
            e.printStackTrace();
            return illustId;
        }

        String json = response.body();
        String newestIllustIdRegex = "(?<=\\{\")[\\d]*(?=\":null)";
        Matcher matcher = Pattern.compile(newestIllustIdRegex).matcher(json);
        if (!matcher.find()) {
            System.err.println("user " + userId + " has no illustration");
            return illustId;
        }
        String newestIllustId = matcher.group(0);

        String illustIdRegex = "(?<=\")[\\d]*(?=\":null)";
        matcher = Pattern.compile(illustIdRegex).matcher(json);
        int curIdx = 1;
        while (matcher.find()) {
            String id = matcher.group(0);
            if (id.compareTo(illustId) <= 0) {
                return newestIllustId;
            }
            System.out.println("\nillustration " + curIdx + "...");
            curIdx++;
            downloadOneIllustration(id,PixivUrl.USER_PAGE + userId);
        }

        userIllustrateMap.put(userId, newestIllustId);
        return newestIllustId;
    }

    /**
     * 下载一个作品
     * @param illustId 作品id
     * @param pageCnt 知道该作品有几页
     */
    void downloadOneIllustration(String illustId, int pageCnt, String referer) {
        // todo pagecnt r18
        if (!checkSession()) {
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
                    .header("Referer", referer)
                    .cookie("PHPSESSID", PHPSESSID)
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
        if (!allowR18 && meta.attr("content").contains("\"tag\":\"R-18\"")) {
            System.out.println("R-18, skip");
            return;
        }
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
     * 下载一个作品
     */
    void downloadOneIllustration(String illustId, String referer) {
        if (!checkSession()) {
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
                    .header("Referer", referer)
                    .cookie("PHPSESSID", PHPSESSID)
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

        if (!allowR18 && meta.attr("content").contains("\"tag\":\"R-18\"")) {
            System.out.println("R-18, skip");
            return;
        }

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
        // 每个作品可能有多个图片，逐个访问下载，不知道pageCnt，试错catch
        int pageIdx = 0;
        while (true) {
            String fileName = illustId + "_p" + pageIdx + suffix;
            if (existFileNames.contains(fileName)) {
                System.out.printf("%s already exist, skip\n", fileName);
            } else {
                System.out.printf("%s... \n", fileName);
                boolean exist = download(illustId, imageSrc + pageIdx + suffix, fileName);
                if (!exist) {
                    break;
                }
            }
            pageIdx++;
        }
    }

    /**
     * 下载一张图片
     * @param illustId 作品id
     * @param url 图片地址
     * @param fileName 最终保存的文件名
     * @return 本图片在pixiv是否存在
     */
    private boolean download(String illustId, String url, String fileName) {
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
        } catch (UncheckedIOException e) {
            System.err.println("download failed, file name: " + fileName);
            downloadFailedFiles.add(fileName);
            e.printStackTrace();
            return true;
        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                System.out.println("404");
                return false;
            }
            System.err.println("download failed, file name: " + fileName);
            e.printStackTrace();
            return true;
        } catch (IOException e) {
            System.err.println("download failed, file name: " + fileName);
            e.printStackTrace();
            return false;
        }

        if (img.length > threshold) {
            // 大过阈值的图片不下载
            System.out.println("img size " + img.length + "B, bigger than threshold, skip");
            return true;
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
        return true;
    }

    /**
     * 下载前检测是否登录
     */
    boolean checkSession() {
        if (PHPSESSID == null || PHPSESSID.equals("")) {
            System.err.println("should set PHPSESSID in crawler_cfg.yml");
            return false;
        }
        return true;
    }

    public static void main(String[] args) {
        Crawler demo = new Crawler();
        String userId = "2039232";
        demo.downloadAllUserIllusts();
//        demo.downloadUserIllustrations(userId, "");
//        demo.downloadOneIllustration("85256262", PixivUrl.BASE_URL);
    }
}
