package com.inn.automate.restImpl;


import com.inn.automate.constants.AutoConstants;
import com.inn.automate.REST.UserRest;
import com.inn.automate.Service.UserService;

import com.inn.automate.utils.AutoUtils;
import com.inn.automate.wrapper.UserWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map; // ✅ Import Map

@RestController
@RequestMapping("/user")
public class UserRestImpl implements UserRest {

    @Autowired
    UserService userService;

    @Override
    public ResponseEntity<String> signUp(@RequestBody Map<String, String> requestMap) {
        try {
            return userService.signUp(requestMap);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * @param requestMap
     * @return
     */
    @Override
    public ResponseEntity<String> login(Map<String, String> requestMap) {
        try{

            return userService.login(requestMap);
        } catch (Exception ex) {
            ex.printStackTrace();

        }
        return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<List<UserWrapper>> getAllUsers() {
        try{
            return userService.getAllUser();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return new ResponseEntity<List<UserWrapper>>(new ArrayList<>(),HttpStatus.INTERNAL_SERVER_ERROR);
    }


    @Override
    public ResponseEntity<String> update(Map<String, String> requestMap) {
        try{
            return userService.update(requestMap);
        }catch(Exception ex){
            ex.printStackTrace();

        }
        return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }


    @Override
    public ResponseEntity<String> checkToken() {
        try{
            return userService.checkToken();
        }catch(Exception ex){
            ex.printStackTrace();
        }
        return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<String> changePassword(Map<String, String> requestMap) {
        try{
            return userService.changePassword(requestMap);

        }catch(Exception ex){
            ex.printStackTrace();
        }
        return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<String> forgotPassword(Map<String, String> requestMap) {
        try{
            return userService.forgotPassword(requestMap);
        }catch(Exception ex){
            ex.printStackTrace();
        }
        return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);

    }

    @Override
    public ResponseEntity<UserWrapper> getCurrentUser() {
        try{
            return userService.getCurrentUser();
        }catch(Exception ex){
            ex.printStackTrace();
        }
        return new ResponseEntity<UserWrapper>((MultiValueMap<String, String>) new ArrayList<>(),HttpStatus.INTERNAL_SERVER_ERROR);
    }
    @Override
    public ResponseEntity<String> uploadImage(String userId, MultipartFile file) {
        try {
            return userService.uploadUserImage(userId, file);
        } catch(Exception ex) {
            ex.printStackTrace();
            return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
