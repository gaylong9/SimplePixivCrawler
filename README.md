# SimplePixivCrawler
Jsoup爬取Pixiv图片（月榜、受男性欢迎榜、指定用户的插图作品）（并上传至服务器）



## 用法

浏览器登录Pixiv后将cookie中的`PHPSESSID`拷贝进`src/main/resources/crawler_cfg.yml`的`PHPSESSID`项。

另外需配置resources下的3个yml文件。

其中：

`crawler_cfg.yml`是对爬虫的配置；

`sftp_cfg.yml`是对发送图片至服务器的配置；

`user_illustrations.yml`是记录下载用户作品进度，格式为`userIllustrations: {'用户id': '最新下载的作品id'}`，执行时将会下载该用户的晚于该作品的新作品。

随后在`Main.main()`中执行方法。



## 参考

登录：后测试发现Jsoup的登录并不好用，所以改用手动copy session cookie至yml。

[参考1](https://www.cnblogs.com/fightfordream/p/6421498.html)

[参考2](https://www.tqwba.com/x_d/jishu/244706.html)



下载图片：

[参考1](https://blog.csdn.net/owaranaiyume/article/details/114667736)

[参考2](https://blog.csdn.net/weixin_45826022/article/details/109406389)

