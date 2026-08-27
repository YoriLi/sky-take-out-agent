package com.sky.aspect;

import com.sky.annotation.AutoFill;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 自定义切面，实现公共字段自动填充处理逻辑
 */
@Aspect
// @Aspect: 标记这是一个切面类。Spring 会解析其中的 @Pointcut 和 @Before 等注解，
// 并在运行时动态生成代理对象来执行拦截逻辑。
@Component
@Slf4j
public class AutoFillAspect {

    /**
     * 切入点
     * com.sky.mapper包下包含AutoFill注解的所有类和方法
     */
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    /*
     * 切入点（Pointcut）：定义拦截规则（“拦谁”）。
     *
     * 表达式解析：
     * 1. execution(* com.sky.mapper.*.*(..))
     *    - 表示拦截 com.sky.mapper 包下所有类的所有方法（任意参数）。
     * 2. && @annotation(com.sky.annotation.AutoFill)
     *    - 表示只拦截那些方法上标注了 @AutoFill 注解的方法。
     * 组合含义：拦截 Mapper 层中所有加了 @AutoFill 注解的方法。
     * 例如：EmployeeMapper.insert() 和 EmployeeMapper.update() 都会被拦截。
     */
    public void autoFillPointCut() {
        // 这个方法体为空，它只是一个“锚点”（名字），供下面的 @Before 引用。
        // 切入点表达式已经写在了 @Pointcut 注解里。
        // 什么不能直接写表达式：@Before("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
        // 这一串字符串太长且容易写错。给它起个名字（autoFillPointCut()）后，
        // 在 @Before 里只需要引用这个名字，一处定义，多处复用
    }

    /**
     * 前置通知，在通知中进行公共字段的赋值
     */
    @Before("autoFillPointCut()")
    /*
     * 前置通知（Before Advice）：在目标方法执行之前执行。
     * 引用上面定义的切入点 autoFillPointCut()，表示“当命中该规则时，执行本方法”。
     * 它把这个普通的autoFill方法标记为“前置通知方法”。
     * Spring AOP在生成代理对象时，会把autoFill方法的代码织入到拦截逻辑中。
     */
    public void autoFill(JoinPoint joinPoint) {
        log.info("开始进行公共字段自动填充");

//        获取到当前拦截的方法上的数据库操作类型
//        获取方法签名对象
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        // 第一步：获取被拦截方法的注解信息（确定是 INSERT 还是 UPDATE）
        // 获取方法签名对象。由于我们知道拦截的是 Mapper 方法，且方法上必定有 @AutoFill，
        // 可以将 Signature 强转为 MethodSignature，以便获取更丰富的方法元数据。
//        获取方法上的注解对象
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);
        // 通过 MethodSignature 获取当前方法的 Method 对象，再调用 getAnnotation 获取方法上的 @AutoFill 注解实例。
        // 注解实例中保存了我们在写注解时指定的 value 值（即 OperationType.INSERT 或 UPDATE）。
        //AutoFill.class)可以区分重载方法写
        // getDeclaredMethod("setCreateTime", LocalDateTime.class)，反射就知道你要找的是 public void setCreateTime(LocalDateTime time) 这个方法
        //作用：检查某个方法（Method 对象）上是否贴了 AutoFill 这张“标签”。
        //getAnnotation(AutoFill.class)
        //返回值：如果贴了，就返回该注解的实例对象（你可以通过它读取注解里的属性值，如 INSERT）；如果没贴，返回 null。
//        //获取数据库操作类型
        OperationType operationType = autoFill.value();

//        获取到当前被拦截的方法的参数---实体对象
        Object[] args = joinPoint.getArgs();
        // 从注解中取出数据库操作类型（INSERT 或 UPDATE）
        if (args == null || args.length == 0) {
            return;
        }
        // 如果没有参数（理论上不会发生），直接返回，不做填充
        Object entity = args[0];

//        转变赋值的数据
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

//        根据当前不同的操作类型，为对应的属性通过反射来赋值
        if (operationType == OperationType.INSERT) {
//            为4个公共字段赋值
            try {
                Method setCreateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
                // 1. 通过反射获取 Entity 类中名为 "setCreateTime" 的方法，参数类型为 LocalDateTime.class。
                Method setCreateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER, Long.class);
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

//              通过反射为对象赋值
                setCreateTime.invoke(entity, now);
                // 执行 invoke 方法，相当于执行 entity.setCreateTime(now); 即把刚刚准备好的 now 和 currentId 塞进 entity 对象中。
                //setCreateTime.invoke(entity, now) 等价于执行 entity.setCreateTime(now);
                //invoke 的作用：它允许你在运行时（Runtime）根据方法名（字符串）去调用方法
                setCreateUser.invoke(entity, currentId);
                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        } else if (operationType == operationType.UPDATE) {
//            为2个公共字段赋值
            try {
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

//              通过反射为对象赋值
                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }


    }
}
