package com.losgai.sys.common.satoken;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * [Sa-Token 权限认证] 配置类
 */
@Configuration
@Slf4j
public class SaTokenConfigure implements WebMvcConfigurer {

    /**
     * 注册 [Sa-Token全局过滤器]
     */
    @Bean
    public SaServletFilter getSaServletFilter() {
        return new SaServletFilter()
                // 指定 拦截路由 与 放行路由
                .addInclude("/**")
                /* 排除掉 /favicon.ico */
                .addExclude("/favicon.ico")

                // 认证函数: 每次请求执行
                .setAuth(obj -> {
                    log.info("---------- 进入Sa-Token全局认证 -----------");
                    // 登录认证 -- 拦截所有路由，并排除/auth 用于开放登录与注册
                    SaRouter.match("/**")
                            .notMatch("/sys/user/auth/**")
                            // 放行支付宝支付结果回调
                            .notMatch("/rental/payment/notify")
                            .notMatch("/rental/payment/return")
                            .notMatchMethod("OPTIONS")
                            .check(r -> StpUtil.checkLogin());

                    // 更多拦截处理方式，请参考“路由拦截式鉴权”章节 */
                })

                // 异常处理函数：每次认证函数发生异常时执行此函数 
                .setError(e -> {
                    log.error("---------- 进入Sa-Token异常处理:{} -----------", e.getMessage());
                    return SaResult.error(e.getMessage());
                })

                // 前置函数：在每次认证函数之前执行（BeforeAuth 不受 includeList 与 excludeList 的限制，所有请求都会进入）
                .setBeforeAuth(r -> {
                    // ---------- 设置一些安全响应头 ----------
                    SaHolder.getResponse()
                            // 服务器名称
                            .setServer("ai")
                            // 是否可以在iframe显示视图： DENY=不可以 | SAMEORIGIN=同域下可以 | ALLOW-FROM uri=指定域名下可以
                            .setHeader("X-Frame-Options", "SAMEORIGIN")
                            // 是否启用浏览器默认XSS防护： 0=禁用 | 1=启用 | 1; mode=block 启用, 并在检查到XSS攻击时，停止渲染页面
                            .setHeader("X-XSS-Protection", "1; mode=block")
                            // 禁用浏览器内容嗅探
                            .setHeader("X-Content-Type-Options", "nosniff")
                            // 允许指定域访问跨域资源
                            .setHeader("Access-Control-Allow-Origin", "*")
                            // 允许所有请求方式
                            .setHeader("Access-Control-Allow-Methods", "*")
                            // 允许的header参数
                            .setHeader("Access-Control-Allow-Headers", "*")
                            // 有效时间
                            .setHeader("Access-Control-Max-Age", "3600");

                    // 如果是预检请求，则立即返回到前端
                    SaRouter.match(SaHttpMethod.OPTIONS)
                            .free(res -> log.info("[OPTIONS]预检请求，不做处理"))
                            .back();

                });
    }

    // 注册 Sa-Token 拦截器，打开注解式鉴权功能
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，打开注解式鉴权功能
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }

}
