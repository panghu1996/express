package com.express.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.express.annotation.AuthorityCheck;
import com.express.annotation.PassToken;
import com.express.annotation.UserLoginToken;
import com.express.entity.SysApiEntity;
import com.express.entity.SysUser;
import com.express.entity.SysUserApiEntity;
import com.express.entity.SysUserTokenEntity;
import com.express.mapper.SysApiDao;
import com.express.mapper.SysUserApiDao;
import com.express.service.SysUserTokenService;
import com.express.service.UserService;
import com.express.util.JWTService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


/**
 * @author
 * @date 2018-07-08 20:41
 */
public class AuthenticationInterceptor implements HandlerInterceptor {
    @Autowired
    SysUserTokenService userTokenService;

    @Autowired
    JWTService jwtService;

    @Autowired
    private SysApiDao sysApiDao;

    @Autowired
    private SysUserApiDao sysUserApiDao;

    @DS("api")
    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object object) throws Exception {
        String url = httpServletRequest.getRequestURI();
        httpServletResponse.setCharacterEncoding("UTF-8");
        httpServletResponse.setContentType("application/json; charset=utf-8");
        JSONObject res = new JSONObject();

        String token = httpServletRequest.getHeader("Authentication");// 从 http 请求头中取出 token
        // 如果不是映射到方法直接通过
        if (!(object instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) object;
        Method method = handlerMethod.getMethod();
        //检查是否有passtoken注释，有则跳过认证
        if (method.isAnnotationPresent(PassToken.class)) {
            PassToken passToken = method.getAnnotation(PassToken.class);
            if (passToken.required()) {
                return true;
            }
        }


        // 执行认证
        if (token == null) {
            httpServletResponse.setCharacterEncoding("UTF-8");
            httpServletResponse.setContentType("application/json; charset=utf-8");

            throw new RuntimeException("无token，请重新登录");
        }
        // 验证
        Boolean tokenFlag = jwtService.decryptToken(token);
        if (!tokenFlag) {

            res.put("code", "501");
            res.put("msg", "无效token");
            PrintWriter out = null;
            out = httpServletResponse.getWriter();
            out.write(res.toString());
            out.flush();
            out.close();
            return false;
        }
        // 获取 token 中的 user id
        String userId;
        try {
            userId = jwtService.getUser(token).getId().toString();
        } catch (JWTDecodeException j) {
            res.put("code", "502");
            res.put("msg", "无效token");
            PrintWriter out = null;
            out = httpServletResponse.getWriter();
            out.write(res.toString());
            out.flush();
            out.close();
            throw new RuntimeException("502，无效token");
        }
        SysUserTokenEntity sysUser = userTokenService.getById(userId);
        if (sysUser == null) {
            res.put("code", "503");
            res.put("msg", "无效token");
            PrintWriter out = null;
            out = httpServletResponse.getWriter();
            out.write(res.toString());
            out.flush();
            out.close();
            throw new RuntimeException("用户不存在，请重新登录");
        } else {
            // 验证token
            Boolean b = jwtService.decryptToken(token);


            if (!b) {
                res.put("code", "502");
                res.put("msg", "无效token");
                PrintWriter out = null;
                out = httpServletResponse.getWriter();
                out.write(res.toString());
                out.flush();
                out.close();
            }
        }
        // 接口鉴权
        AuthorityCheck annotation = method.getAnnotation(AuthorityCheck.class);
        if(null != annotation){
            // 接口需要的权限
            String value = annotation.value();
            // 查所有可以访问的接口
            List<SysApiEntity> sysApiEntities = sysApiDao.selectList(
                    new QueryWrapper<SysApiEntity>()
                            .eq("api_name", value)
                            .eq("status", 1)
                            .eq("is_del", 0)
                            .isNotNull("api_name"));

            if (null != sysApiEntities && sysApiEntities.size() > 0) {
                List<Long> apiList = new ArrayList<>();
                sysApiEntities.forEach(api -> apiList.add(api.getId()));

                Integer count = sysUserApiDao.selectCount(new QueryWrapper<SysUserApiEntity>()
                        .eq("user_id", userId)
                        .eq("status", 1)
                        .eq("is_del", 0)
                        .in("api_id", apiList));

                if (count < 1){
                    res.put("code", "401");
                    res.put("msg", "网络忙，请联系管理员");
                    PrintWriter out = null;
                    out = httpServletResponse.getWriter();
                    out.write(res.toString());
                    out.flush();
                    out.close();
                    return false;
                }
            }else {
                res.put("code", "401");
                res.put("msg", "网络忙，请联系管理员");
                PrintWriter out = null;
                out = httpServletResponse.getWriter();
                out.write(res.toString());
                out.flush();
                out.close();
                return false;
            }

        }

        return true;
    }


    @Override
    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception e) throws Exception {

    }
}
