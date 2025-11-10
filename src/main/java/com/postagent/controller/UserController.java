package com.postagent.controller;

import com.postagent.common.UserConstant;
import com.postagent.entity.UserInfo;
import com.postagent.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserInfo userInfo) {
        userService.register(userInfo);
        return ResponseEntity.ok("register success");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserInfo userInfo) {
        // 校验用户名密码是否正确
        UserInfo userInfoFromDb = userService.findByUsername(userInfo.getUsername());
        if (userInfoFromDb == null) {
            return ResponseEntity.badRequest().body("username not found");
        }
        if (!userInfoFromDb.getPassword().equals(DigestUtils.md5DigestAsHex((UserConstant.SALT + userInfo.getPassword()).getBytes()))) {
            return ResponseEntity.badRequest().body("password not match");
        }
        return ResponseEntity.ok("login success");
    }

    @GetMapping("/logout")
    public ResponseEntity<?> getUserInfo(@RequestParam("id") Long id) {
        UserInfo userInfo = userService.findById(id);
        if (userInfo == null) {
            return ResponseEntity.badRequest().body("user id not found");
        }
        return ResponseEntity.ok(userInfo);
    }

}