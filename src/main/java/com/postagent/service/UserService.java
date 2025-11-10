package com.postagent.service;

import com.postagent.entity.UserInfo;
import com.postagent.repository.UserInfoRepository;
import jakarta.annotation.Resource;
import org.bouncycastle.jcajce.provider.asymmetric.rsa.RSAUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import static com.postagent.common.UserConstant.SALT;

@Service
public class UserService {
    @Resource
    private UserInfoRepository userInfoRepository;

    /**
     * 注册用户
     * @param userInfo 用户信息
     */
    public void register(UserInfo userInfo) {
        String username = userInfo.getUsername();
        UserInfo userInfoFromDb = findByUsername(username);
        if (userInfoFromDb != null) {
            throw new IllegalArgumentException("username already exists");
        }
        String password = userInfo.getPassword();
        // 密码加密
        String handledPassword = DigestUtils.md5DigestAsHex((SALT + password).getBytes());
        userInfo.setPassword(handledPassword);
        userInfoRepository.save(userInfo);
    }

    /**
     * 根据用户名查询用户信息
     * @param username 用户名
     * @return 用户信息
     */
    public UserInfo findByUsername(String username) {
        return userInfoRepository.findByUsername(username);
    }

    public UserInfo findById(Long id) {
        return userInfoRepository.findById(id).orElse(null);
    }
}