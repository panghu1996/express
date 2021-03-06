package com.express.util;


import com.auth0.jwt.JWT;

import com.auth0.jwt.JWTCreator.Builder;

import com.auth0.jwt.JWTVerifier;

import com.auth0.jwt.algorithms.Algorithm;

import com.auth0.jwt.exceptions.*;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.express.common.Payload;
import com.express.entity.SysUser;

import com.express.entity.SysUserApiEntity;
import com.express.entity.SysUserTokenEntity;
import com.express.service.SysUserTokenService;
import com.express.service.UserService;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.util.*;

@Service("JWTService")
public class JWTService {


    @Value("${jwt.secretKey}")
    private String secret;

    @Value("${jwt.tokenOutTime}")
    private Long tokenOutTime;

    private String issuer = "USERSERVICE";//发布者

    private String subject = "userLoginToken";//主题

    private String audience = "APP";//签名的观众 也可以理解谁接受签名的

    private Map<String, String> claims;//自定义签名


    // 用户
    @Autowired
    private UserService userService;

    // 用户和token
    @Autowired
    private SysUserTokenService userTokenService;


    /**
     * 创建 hour小时后过期的Token
     *
     * @param claims
     * @param hour
     * @return
     */

    public String createToken(Map<String, String> claims, int hour) {

        Payload createPayload = this.createPayload(1);

        createPayload.setClaims(claims);

        Algorithm hmac256 = Algorithm.HMAC256(this.getSecret());

        return createToken(createPayload, hmac256);

    }

    /**
     * 根据负载和算法创建Token
     *
     * @param payload
     * @param algorithm
     * @return
     */

    public String createToken(Payload payload, Algorithm algorithm) {


        Builder headBuilder = createHeaderBuilder(algorithm);

        Builder publicClaimbuilder = addPublicClaimBuilder(headBuilder, payload);

        Builder privateClaimbuilder = addPrivateClaimbuilder(publicClaimbuilder, payload);

        String token = privateClaimbuilder.sign(algorithm);

        return token;

    }

    /**
     * 创建自定小时后过期的负载
     *
     * @param hour
     * @return
     */

    public Payload createPayload(int hour) {

        Payload payload = new Payload();

        payload.setIssuer(this.getIssuer());

        payload.setSubject(this.getSubject());
        ArrayList<String> strings = new ArrayList<>();
        strings.add(this.getAudience());
        payload.setAudience(strings);

        this.setIssuedAtAndExpiresAt(new Date(), hour, payload);

        return payload;

    }

    /**
     * 创建自定小时后过期的负载
     *
     * @param hour
     * @return
     */

    public Payload createPayload(String issuer, String subject, String audience, Date date, int hour) {

        Payload payload = new Payload();

        payload.setIssuer(issuer);

        payload.setSubject(subject);

        ArrayList<String> strings = new ArrayList<>();
        strings.add(this.getAudience());
        payload.setAudience(strings);

        this.setIssuedAtAndExpiresAt(date, hour, payload);

        return payload;

    }

    /**
     * 添加私有声明
     *
     * @param builder
     * @param payload
     * @return
     */

    private Builder addPrivateClaimbuilder(Builder builder, Payload payload) {

        Map<String, String> claims = payload.getClaims();

        if (!CollectionUtils.isEmpty(claims)) {

            claims.forEach((k, v) -> {

                builder.withClaim(k, (String) v);

            });

        }

        return builder;

    }

    /**
     * 添加公共声明
     *
     * @param builder
     * @param payload
     * @return
     */

    private Builder addPublicClaimBuilder(Builder builder, Payload payload) {

        if (!StringUtils.isEmpty(payload.getIssuer())) {

            builder.withIssuer(payload.getIssuer());

        }


        if (!StringUtils.isEmpty(payload.getSubject())) {

            builder.withSubject(payload.getSubject());

        }


        if (payload.getIssuedAt() != null) {

            builder.withIssuedAt(payload.getIssuedAt()); //生成签名的时间

        }


        if (payload.getExpiresAt() != null) {

            builder.withExpiresAt(payload.getExpiresAt());//签名过期的时间

        }


        if (CollectionUtils.isEmpty(payload.getAudience())) {

            payload.getAudience().forEach((s) -> {

                builder.withAudience(s);

            });

        }


        return builder;

    }

    /**
     * 创建JWT 头部信息
     *
     * @param algorithm
     * @return
     */

    private Builder createHeaderBuilder(Algorithm algorithm) {

        Builder builder = JWT.create().withHeader(buildJWTHeader(algorithm));

        return builder;

    }

    /**
     * 校验Token
     *
     * @param token
     * @return
     */

    public Payload verifyToken(String token) {

        DecodedJWT jwt = null;

        Payload payload = null;

        try {

            jwt = getDecodedJWT(token);

            payload = getPublicClaim(jwt);

            payload = getPrivateClaim(jwt, payload);

        } catch (AlgorithmMismatchException e) {

            throw e;

        } catch (TokenExpiredException e) {

            throw e;

        } catch (Exception e) {

            throw e;

        }

        return payload;

    }

    /**
     * 获取JWT 私有声明
     *
     * @param jwt
     * @param payload
     * @return
     */

    private Payload getPrivateClaim(DecodedJWT jwt, Payload payload) {

        Map<String, String> claims = new HashMap<String, String>();

        jwt.getClaims().forEach((k, v) -> {

            String asString = v.asString();

            claims.put(k, asString);

        });

        payload.setClaims(claims);

        return payload;

    }

    /**
     * 获取JWT 公共声明
     *
     * @param jwt
     * @return
     */

    private Payload getPublicClaim(DecodedJWT jwt) {

        Payload payload = new Payload();

        payload.setIssuer(jwt.getIssuer());

        payload.setSubject(jwt.getSubject());

        payload.setAudience(jwt.getAudience());

        payload.setIssuedAt(jwt.getIssuedAt());

        payload.setExpiresAt(jwt.getExpiresAt());

        return payload;

    }

    /**
     * 获取 DecodedJWT
     *
     * @param token
     * @return
     */

    private DecodedJWT getDecodedJWT(String token) {

        JWTVerifier verifier = JWT.require(Algorithm.HMAC256(this.getSecret())).build();

        DecodedJWT jwt = verifier.verify(token);

        return jwt;

    }

    /**
     * 构建JWT头部Map信息
     *
     * @param algorithm
     * @return
     */

    private Map<String, Object> buildJWTHeader(Algorithm algorithm) {

        Map<String, Object> map = new HashMap<String, Object>();

        map.put("alg", algorithm.getName());

        map.put("typ", "JWT");

        return map;

    }


    /**
     * 根据发布时间设置过期时间
     *
     * @param issuedAt
     * @param hour
     * @param payload
     */

    public void setIssuedAtAndExpiresAt(Date issuedAt, Integer hour, Payload payload) {

        payload.setIssuedAt(issuedAt);

        payload.setExpiresAt(getAfterDateByHour(issuedAt, hour));

    }

    /**
     * 返回一定时间后的日期
     *
     * @param date 开始计时的时间
     * @param hour 增加的小时
     * @return
     */

    public Date getAfterDateByHour(Date date, int hour) {

        if (date == null) {

            date = new Date();

        }

        Date afterDate = getAfterDate(date, 0, 0, 0, hour, 0, 0);

        return afterDate;

    }

    public Date getAfterDateByMinute(Date date, int minute) {

        if (date == null) {

            date = new Date();

        }

        Date afterDate = getAfterDate(date, 0, 0, 0, 0, minute, 0);

        return afterDate;

    }

    /**
     * 返回一定时间后的日期
     *
     * @param date   开始计时的时间
     * @param year   增加的年
     * @param month  增加的月
     * @param day    增加的日
     * @param hour   增加的小时
     * @param minute 增加的分钟
     * @param second 增加的秒
     * @return
     */

    public Date getAfterDate(Date date, int year, int month, int day, int hour, int minute, int second) {

        if (date == null) {

            date = new Date();

        }


        Calendar cal = new GregorianCalendar();


        cal.setTime(date);

        if (year != 0) {

            cal.add(Calendar.YEAR, year);

        }

        if (month != 0) {

            cal.add(Calendar.MONTH, month);

        }

        if (day != 0) {

            cal.add(Calendar.DATE, day);

        }

        if (hour != 0) {

            cal.add(Calendar.HOUR_OF_DAY, hour);

        }

        if (minute != 0) {

            cal.add(Calendar.MINUTE, minute);

        }

        if (second != 0) {

            cal.add(Calendar.SECOND, second);

        }

        return cal.getTime();

    }


    /**
     * 获得token中的信息无需secret解密也能获得
     *
     * @return token中包含的实体类
     */

    public SysUserTokenEntity getUser(String token) {

        DecodedJWT jwt = JWT.decode(token);

        SysUserTokenEntity user = new SysUserTokenEntity();
        Map<String, Claim> claims = jwt.getClaims();
        String userId = claims.get("userId").asString();
        user = userTokenService.getById(userId);

        return user;

    }

//    public SysUserTokenEntity getUser(String token) {
//
//        DecodedJWT jwt = JWT.decode(token);
//
//        SysUserTokenEntity user = new SysUserTokenEntity();
//        Map<String, Claim> claims = jwt.getClaims();
//        String userId = claims.get("userId").asString();
//        user = userTokenService.getById(userId);
//
//        return user;
//
//    }


    public void setClaims(Map<String, String> claims) {

        this.claims = claims;

    }


    public Map<String, String> getClaims() {

        return claims;

    }


    public String getIssuer() {

        return issuer;

    }


    public void setIssuer(String issuer) {

        this.issuer = issuer;

    }


    public String getSubject() {

        return subject;

    }


    public void setSubject(String subject) {

        this.subject = subject;

    }


    public String getAudience() {

        return audience;

    }


    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getToken(SysUserTokenEntity user) {

        String token = null;
        try {
            Long currentTime = System.currentTimeMillis();
            String tokenId = new StringBuilder(String.valueOf(currentTime)).append(user.getId()).toString();
            Date expiresAt = new Date(currentTime + tokenOutTime);
            token = JWT.create()
                    // 发行者
                    .withIssuer("")
                    // 自定义属性
                    .withClaim("userId", user.getId().toString())
                    // 签发时间
                    .withIssuedAt(new Date(currentTime))
                    // 唯一id
                    .withJWTId(tokenId)
                    // token过期时间
                    .withExpiresAt(expiresAt)
                    // 使用了HMAC256加密算法。
                    .sign(Algorithm.HMAC256(secret));
        } catch (JWTCreationException exception) {
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        return token;
    }

    /**
     * 验证token
     *
     * @param token
     * @return
     */
    public Boolean decryptToken(final String token) {
        if (token == null) {
            return false;
        }
        DecodedJWT jwt = null;
        try {
            // 使用了HMAC256加密算法。
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer("")
                    .build();
            // 对字段进行校验，超时抛出JWTVerificationException类的子类InvalidClaimException
            jwt = verifier.verify(token);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

}
