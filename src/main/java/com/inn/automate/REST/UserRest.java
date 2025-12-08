    package com.inn.automate.REST;


    import com.inn.automate.POJO.User;
    import com.inn.automate.wrapper.UserWrapper;
    import org.springframework.http.MediaType;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.*;
    import org.springframework.web.multipart.MultipartFile;

    import java.util.List;
    import java.util.Map;

    @RequestMapping(path="/user")

    public interface UserRest {

        @PostMapping(path = "/signup")
        ResponseEntity<String> signUp(@RequestBody Map<String,String> requestMap);


        @PostMapping(path = "/login")
        public ResponseEntity<String> login(@RequestBody(required = true) Map<String,String> requestMap);

        @GetMapping(path="/get")
        public ResponseEntity<List<UserWrapper>> getAllUsers();

        @PostMapping(path="/update")
        public ResponseEntity<String> update(@RequestBody(required = true)Map<String,String>RequestMap);

        @GetMapping(path="/checkToken")
        ResponseEntity<String> checkToken();

        @PostMapping(path="/changePassword")
        ResponseEntity<String> changePassword(@RequestBody Map<String,String>RequestMap);

        @PostMapping(path="/forgotPassword")
        ResponseEntity<String> forgotPassword(@RequestBody Map<String,String>RequestMap);

        @GetMapping(path="/current")
        ResponseEntity<UserWrapper> getCurrentUser();

        @PostMapping(path="/uploadImage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        ResponseEntity<String> uploadImage(
                @RequestPart("userId") String userId,
                @RequestPart("file") MultipartFile file
        );

    }
