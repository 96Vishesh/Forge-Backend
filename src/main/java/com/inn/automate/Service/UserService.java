package com.inn.automate.Service;


import com.inn.automate.wrapper.UserWrapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface UserService {

    ResponseEntity<String> signUp(Map<String,String> requestMap);
    ResponseEntity<String> login(Map<String,String> requestMap);
    ResponseEntity<List<UserWrapper>> getAllUser();
    ResponseEntity<String> update(Map<String,String> requestMap);
    ResponseEntity<String> checkToken();
    ResponseEntity<String> changePassword(Map<String,String> requestMap);
    ResponseEntity<String> forgotPassword(Map<String,String> requestMap);

    ResponseEntity<UserWrapper> getCurrentUser();

    ResponseEntity<String> uploadUserImage(String userId, MultipartFile file);
}

