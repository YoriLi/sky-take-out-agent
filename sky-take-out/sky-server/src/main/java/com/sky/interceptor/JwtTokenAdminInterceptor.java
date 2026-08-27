package com.sky.interceptor;

import com.sky.constant.JwtClaimsConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.entity.Employee;
import com.sky.mapper.EmployeeMapper;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * jwt令牌校验的拦截器
 */
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private EmployeeMapper employeeMapper;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String token = request.getHeader(jwtProperties.getAdminTokenName());
        try {
            log.info("jwt校验: admin token present={}", token != null && !token.isEmpty());
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());

            // 每次请求复核账号状态：被禁用的员工，其未过期令牌应立即失效
            Employee employee = employeeMapper.getById(empId);
            if (employee == null || StatusConstant.DISABLE.equals(employee.getStatus())) {
                log.warn("账号不存在或已被禁用，拒绝访问 empId={}", empId);
                response.setStatus(401);
                return false;
            }

            log.info("当前员工id：{}", empId);
            BaseContext.setCurrentId(empId);
            return true;
        } catch (Exception ex) {
            response.setStatus(401);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }
}
