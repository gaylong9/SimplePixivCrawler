package org.example;

import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NewCrawler {
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
    // 图片大小大于阈值的的跳过
    int threshold;
    // 作品中图片数大于阈值的跳过
    int pageCntThreshold;
    // 是否下载R18作品
    boolean allowR18;
    ArrayList<String> downloadFailedFiles = new ArrayList<>();
    int imgNumOnStart;
    String ymlFileName = "crawler_cfg.yml";
    WebClient webClient;

    NewCrawler() {
        // 从resources/crawler_cfg.yml读取配置项
        Yaml yaml = new Yaml();
        File ymlFile = new File(Crawler.class.getResource("/" + ymlFileName).getFile());
        if (!ymlFile.exists()) {
            System.err.println(ymlFileName + " is not exists");
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(ymlFile), StandardCharsets.UTF_8)) {
            Map<String, Object> map = yaml.load(reader);
            int UAIdx = ThreadLocalRandom.current().nextInt(0, PixivUrl.User_Agents.length);
            UserAgent = PixivUrl.User_Agents[UAIdx];
            filePath = (String) map.get("filePath");
            id = (String) map.get("id");
            password = (String) map.get("password");
            threshold = (int) map.get("threshold");
            pageCntThreshold = (int) map.get("pageCntThreshold");
            allowR18 = (boolean) map.get("allowR18");
        } catch (IOException e) {
            System.err.println("load yml failed");
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

        webClient = new WebClient(BrowserVersion.CHROME, "127.0.0.1", 7890);
        webClient.getOptions().setUseInsecureSSL(true);
        webClient.getOptions().setJavaScriptEnabled(true);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(true);
        webClient.getOptions().setActiveXNative(true);
        webClient.getOptions().setTimeout(20 * 1000);
        webClient.setJavaScriptTimeout(10 * 1000);
        webClient.waitForBackgroundJavaScript(30*1000);
        webClient.setAjaxController(new NicelyResynchronizingAjaxController());
        webClient.getOptions().setDoNotTrackEnabled(false);
    }

    void login() {
        // 获取postkey
        WebRequest loginPageRequest;
        try {
            loginPageRequest = new WebRequest(new URL(PixivUrl.LOGIN_PAGE_URL));
        } catch (MalformedURLException e) {
            System.err.println("loginPageRequest build failed");
            e.printStackTrace();
            return;
        }
        loginPageRequest.setAdditionalHeader("Accept", "*/*");
        loginPageRequest.setAdditionalHeader("Accept-Encoding", "gzip, deflate");
        loginPageRequest.setAdditionalHeader("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3");
        loginPageRequest.setAdditionalHeader("User-Agent", UserAgent);
        loginPageRequest.setAdditionalHeader("Referer", PixivUrl.LOGIN_PAGE_URL);
        loginPageRequest.setHttpMethod(HttpMethod.GET);
        HtmlPage loginPage;
        try {
            loginPage = webClient.getPage(loginPageRequest);
        } catch (IOException e) {
            System.err.println("get login page failed");
            e.printStackTrace();
            return;
        }
        Document loginPageDocument = Jsoup.parse(loginPage.asXml());
        System.out.println(loginPageDocument.html());
        String json = Objects.requireNonNull(loginPageDocument.body().getElementById("init-config")).attr("value");
        String postKeyRegex = "(?<=\"pixivAccount.postKey\":\")[\\w]*";
        Matcher matcher = Pattern.compile(postKeyRegex).matcher(json);
        if (!matcher.find()) {
            System.err.println("postKey not found, login failed");
            return;
        }
        String postKey = matcher.group(0);
        System.out.println("postkey: " + postKey);

        // 登录
        ArrayList<NameValuePair> data = new ArrayList<NameValuePair>();
        data.add(new NameValuePair("pixiv_id", id));
        data.add(new NameValuePair("password", password));
        data.add(new NameValuePair("return_to", "https://www.pixiv.net/"));
        data.add(new NameValuePair("post_key", postKey));
        data.add(new NameValuePair("captcha", ""));
        data.add(new NameValuePair("g_recaptcha_response", ""));
        data.add(new NameValuePair("source", "accounts"));
        WebRequest loginRequest;
        try {
            loginRequest = new WebRequest(new URL(PixivUrl.LOGIN_API_URL));

        } catch (MalformedURLException e) {
            System.err.println("loginRequest build failed");
            e.printStackTrace();
            return;
        }
        loginRequest.setAdditionalHeader("Accept", "*/*");
        loginRequest.setAdditionalHeader("Accept-Encoding", "gzip, deflate");
        loginRequest.setAdditionalHeader("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3");
        loginRequest.setAdditionalHeader("User-Agent", UserAgent);
        loginRequest.setAdditionalHeader("Referer", PixivUrl.LOGIN_PAGE_URL);
        loginRequest.setRequestParameters(data);
        loginRequest.setHttpMethod(HttpMethod.POST);
        Page loginRes;
        try {
            loginRes = webClient.getPage(loginRequest);
        } catch (IOException e) {
            System.err.println("get login res failed");
            e.printStackTrace();
            return;
        }

        WebResponse loginResponse = loginRes.getWebResponse();
        System.out.println(loginResponse.getContentAsString());
        System.out.println(loginResponse.getResponseHeaders());
//        Document loginDocument = Jsoup.parse();
//        System.out.println(loginDocument.body());
    }

    public static void main(String[] args) {
        NewCrawler demo = new NewCrawler();
        demo.login();
    }
}
