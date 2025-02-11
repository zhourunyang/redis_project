package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone))
            //不符合，返回错误
            return Result.fail("手机号格式错误!");

        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到session
        //session.setAttribute("code", code);

        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY +phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送验证码
        log.debug("发送验证码成功:{}",code);
        //返回结果
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone))
            return Result.fail("手机号格式错误");
        //校验验证码
        //Object cachaCode = session.getAttribute("code"); session版
        String cachaCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();


        if(cachaCode == null || !cachaCode.equals(code))
            //不一致，报错
            return Result.fail("验证码错误");
        //一致，判断用户是否存在  select * from tb_user where phone =
        User user = userMapper.selectByPhone(loginForm);
        if(user == null)
            //不存在，新增用户
            user = createUserWithPhone(phone);

        //保存到session
        //session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));

        //保存到redis
        //生成随机token，作为登录令牌
        String token = UUID.randomUUID().toString();

        //将user对象转为HASH存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        //stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+token,userMap);
        // 手动将非 String 类型的值转换为 String 类型
        Map<String, String> stringUserMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : userMap.entrySet()) {
            stringUserMap.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        stringRedisTemplate.opsForHash().putAll("login:user:" + token, stringUserMap);


        //设置有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok();
    }

    public User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setPassword("");
        user.setNickName("user_" + RandomUtil.randomString(6));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.save(user);
        return user;
    }
}
