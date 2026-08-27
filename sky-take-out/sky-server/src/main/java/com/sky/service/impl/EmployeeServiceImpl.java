package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.dto.PasswordEditDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordEditFailedException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import com.sky.vo.EmployeeVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 密码比对：兼容明文/MD5，成功后升级为 BCrypt；异常类型与原先一致
        if (!matchesAndUpgradePassword(password, employee)) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象
        return employee;
    }

    /**
     * 校验密码：BCrypt / 遗留 MD5 / 明文；匹配成功后将旧格式升级为 BCrypt。
     */
    private boolean matchesAndUpgradePassword(String rawPassword, Employee employee) {
        String stored = employee.getPassword();
        if (stored == null) {
            return false;
        }

        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, stored);
        }

        String md5 = DigestUtils.md5DigestAsHex(rawPassword.getBytes());
        if (md5.equalsIgnoreCase(stored) || rawPassword.equals(stored)) {
            Employee update = Employee.builder()
                    .id(employee.getId())
                    .password(passwordEncoder.encode(rawPassword))
                    .updateTime(LocalDateTime.now())
                    .build();
            employeeMapper.update(update);
            employee.setPassword(update.getPassword());
            return true;
        }
        return false;
    }

    /**
     * 新增员工
     * @param employeeDTO
     */
    @Override
    public void save(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);

        employee.setStatus(StatusConstant.ENABLE);
        // 默认密码统一用 BCrypt 存储，与登录校验一致
        employee.setPassword(passwordEncoder.encode(PasswordConstant.DEFAULT_PASSWORD));

        Long currentId = BaseContext.getCurrentId();
        employee.setCreateTime(LocalDateTime.now());
        employee.setUpdateTime(LocalDateTime.now());
        employee.setCreateUser(currentId);
        employee.setUpdateUser(currentId);

        employeeMapper.insert(employee);
    }

    /**
     * 员工分页查询
     * @param employeePageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());

        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);

        long total = page.getTotal();
        List<Employee> records = page.getResult();
        // 列表接口用 VO 承载（结构上无 password），并对手机号/身份证号脱敏
        List<EmployeeVO> voList = records.stream().map(this::toMaskedVO).collect(Collectors.toList());

        return new PageResult(total, voList);
    }

    /**
     * 启用禁用员工账户
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        Employee employee = Employee.builder()
                .status(status)
                .id(id)
                .build();
        employeeMapper.update(employee);
    }

    /**
     * 根据iD查询用户信息
     * @param id
     * @return
     */
    @Override
    public EmployeeVO getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        if (employee == null) {
            throw new AccountNotFoundException(MessageConstant.EMPLOYEE_NOT_FOUND);
        }
        // 详情用于编辑回填，保留手机号/身份证原值，但结构上不含 password
        return toVO(employee);
    }

    /**
     * Employee → EmployeeVO（不含 password）。
     */
    private EmployeeVO toVO(Employee e) {
        EmployeeVO vo = new EmployeeVO();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }

    /**
     * 列表展示用 VO：对手机号、身份证号做脱敏。
     */
    private EmployeeVO toMaskedVO(Employee e) {
        EmployeeVO vo = toVO(e);
        vo.setPhone(maskTail(e.getPhone(), 4));
        vo.setIdNumber(maskTail(e.getIdNumber(), 4));
        return vo;
    }

    /**
     * 保留末 keepTail 位，其余以 * 遮蔽；长度不足时整体遮蔽。
     */
    private String maskTail(String value, int keepTail) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int len = value.length();
        if (len <= keepTail) {
            return repeat('*', len);
        }
        return repeat('*', len - keepTail) + value.substring(len - keepTail);
    }

    private String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 编辑员工信息
     * @param employeeDTO
     */
    @Override
    public void update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);

        employee.setUpdateTime(LocalDateTime.now());
        employee.setUpdateUser(BaseContext.getCurrentId());
        employeeMapper.update(employee);
    }

    /**
     * 修改当前登录员工的密码。empId 一律取自登录态，忽略前端传入的 empId，避免越权改他人密码。
     */
    @Override
    public void editPassword(PasswordEditDTO passwordEditDTO) {
        Long empId = BaseContext.getCurrentId();
        if (empId == null) {
            throw new PasswordEditFailedException(MessageConstant.PASSWORD_EDIT_FAILED);
        }

        Employee employee = employeeMapper.getById(empId);
        if (employee == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 校验旧密码（兼容 BCrypt/遗留 MD5/明文，匹配成功会顺带升级为 BCrypt）
        if (!matchesAndUpgradePassword(passwordEditDTO.getOldPassword(), employee)) {
            throw new PasswordEditFailedException(MessageConstant.PASSWORD_ERROR);
        }

        String newPassword = passwordEditDTO.getNewPassword();
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new PasswordEditFailedException("新密码长度不能少于6位");
        }

        Employee update = Employee.builder()
                .id(empId)
                .password(passwordEncoder.encode(newPassword))
                .updateTime(LocalDateTime.now())
                .build();
        employeeMapper.update(update);
    }

}
