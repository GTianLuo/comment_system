package com.utils;

public class RedisConstants {

    public static final String LOGIN_CODE_KEY = "login:code:";

    public static final Long LOGIN_CODE_TTL = 60L;

    public static final String LOGIN_TOKEN_KEY = "login:user:";

    public static final Long LOGIN_TOKEN_TTL = 30L;

    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final Long CATCH_SHOP_TTL = 30L;

    public static final Long CATCH_NULL_TTL = 30L;

    public static final String SHOP_SPINLOCK_KEY = "shop:spinlock:";



}
