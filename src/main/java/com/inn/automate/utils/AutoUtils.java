package com.inn.automate.utils;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class AutoUtils {
    private AutoUtils() {

    }
    public static ResponseEntity<String> getResponseEntity(String responseMessage, HttpStatus httpStatus) {
        return new ResponseEntity<String>("{\"message\":\""+responseMessage+"\"}", httpStatus);
    }
}
