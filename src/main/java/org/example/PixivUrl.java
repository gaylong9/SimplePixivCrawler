package org.example;

public class PixivUrl {
    static final String BASE_URL = "https://www.pixiv.net";
    static final String ARTWORK_URL = "https://www.pixiv.net/artworks/";
    static final String WEEKLY_URL = "https://www.pixiv.net/ranking.php?mode=weekly&content=illust";
    static final String MONTHLY_URL = "https://www.pixiv.net/ranking.php?mode=monthly&content=illust";
    static final String LOGIN_PAGE_URL = "https://accounts.pixiv.net/login?lang=zh&source=pc&view_type=page&ref=wwwtop_accounts_index";
    static final String LOGIN_API_URL = "https://accounts.pixiv.net/api/login?lang=zh";
    static final String[] User_Agents = new String[] {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.71 Safari/537.36",
            "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:48.0) Gecko/20100101 Firefox/48.0"
    };
}