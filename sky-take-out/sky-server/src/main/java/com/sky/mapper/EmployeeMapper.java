package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
// MyBatis 的 Mapper 接口，定义数据访问契约。
// 具体 SQL 语句配置在 resources/mapper/EmployeeMapper.xml 中，通过 namespace（接口全限定名）与接口绑定。
public interface EmployeeMapper {

    /**
     * 根据用户名查询员工
     * @param username
     * @return
     */
    Employee getByUsername(@Param("username") String username);
    // 注意：这个方法没有 @Select 注解，SQL 在 XML 中：
    // <select id="getByUsername" resultType="Employee">
    //   select * from employee where username = #{username}
    // </select>
    // @Param("username") 保证了 XML 中的 #{username} 能正确拿到参数值。
    /**
     * 插入员工数据
     * @param employee
     */
    @AutoFill(OperationType.INSERT)
    // 自定义注解：告诉 AOP 切面，执行该方法前要进行“插入”类型的自动填充。
    // 切面会拦截该方法，通过反射自动为 employee 对象设置：
    // createTime、updateTime（当前时间）、createUser、updateUser（当前操作员ID）。
    void insert(Employee employee);

    /**
     * 员工分页查询
     * @param employeePageQueryDTO
     * @return
     */
    Page<Employee> pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 启用禁用员工账户,编辑员工信息
     * @param employee
     */
    @AutoFill(OperationType.UPDATE)
    void update(Employee employee);

    /**
     * 根据iD查询用户信息
     * @param id
     * @return
     */
    Employee getById(@Param("id") Long id);

}
