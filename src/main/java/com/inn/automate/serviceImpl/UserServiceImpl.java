package com.inn.automate.serviceImpl;

import com.inn.automate.JWT.CustomerUsersDetailService;
import com.inn.automate.JWT.JwtFilter;
import com.inn.automate.JWT.JwtUtil;
import com.inn.automate.POJO.User;
import com.inn.automate.constants.AutoConstants;
import com.inn.automate.DAO.UserDAO;
import com.inn.automate.Service.UserService;
import com.inn.automate.utils.EmailUtils;
import com.inn.automate.utils.AutoUtils;
import com.inn.automate.wrapper.UserWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDAO userDao;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private CustomerUsersDetailService customerUsersDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtFilter jwtFilter;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    EmailUtils emailUtils;

    @Override
    public ResponseEntity<String> signUp(Map<String, String> requestMap) {
        log.info("Inside signup {}", requestMap);

        try {
            if (validateSignUp(requestMap)) {
                User user = userDao.findByEmailId(requestMap.get("email"));
                if (Objects.isNull(user)) {

                    User newUser = getUserFromMap(requestMap);
                    newUser.setPassword(passwordEncoder.encode(requestMap.get("password"))); // Encrypt password
                    userDao.save(newUser);

                    // ✅ Send welcome/registration email
                    new Thread(() -> {
                        try {
                            String subject = "Registration Successful - FlowForge System";
                            String message = "Dear " + newUser.getName() +
                                    ",\n\nYour registration was successful. You will be able to log in once an admin approves your account.\n\n" +
                                    "Thank you,\nFlowForge Team";

                            emailUtils.sendSimpleMessage(jwtFilter.getCurrentUser(), subject, message, Collections.singletonList(newUser.getEmail()));
                        } catch (Exception e) {
                            log.error("Failed to send registration email: ", e);
                        }
                    }).start();
                    return AutoUtils.getResponseEntity("Successfully Registered. Please wait for admin approval.", HttpStatus.OK);
                } else {
                    return AutoUtils.getResponseEntity("Email already exists", HttpStatus.BAD_REQUEST);
                }
            } else {
                return AutoUtils.getResponseEntity(AutoConstants.INVALID_DATA, HttpStatus.BAD_REQUEST);
            }
        } catch (Exception ex) {
            log.error("Error in signup: ", ex);
        }
        return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    private User getUserFromMap(Map<String, String> requestMap) {
        User user = new User();
        user.setId(generateUserId());
        user.setName(requestMap.get("name"));
        user.setContactNumber(requestMap.get("contactNumber"));
        user.setEmail(requestMap.get("email"));
        user.setPassword(requestMap.get("password"));
        user.setStatus("false"); // Default: User needs admin approval
        user.setRole("user");
        return user;
    }

    //    private String generatePrefixedId() {
//        String generatedId;
//        do {
//            String uniquePart = UUID.randomUUID().toString().replaceAll("[^0-9]", "");
//            if (uniquePart.length() < 6) {
//                uniquePart = String.format("%06d", Integer.parseInt(uniquePart));
//            } else {
//                uniquePart = uniquePart.substring(0, 6);
//            }
//            generatedId = "U" + uniquePart;
//        } while (userDao.existsById(generatedId));
//
//        return generatedId;
//    }
    private String generateUserId() {
        String lastId = userDao.getLastUserId(); // Example: "S000129"
        int nextIdNumber = 1;

        if (lastId != null && lastId.startsWith("U")) {
            String numberPart = lastId.substring(1); // remove "S"
            try {
                nextIdNumber = Integer.parseInt(numberPart) + 1;
            } catch (NumberFormatException e) {
                // Handle corrupted data
                nextIdNumber = 1;
            }
        }
        // Format with leading zeros
        return String.format("U%06d", nextIdNumber);
    }


    private boolean validateSignUp(Map<String, String> requestMap) {
        return requestMap.containsKey("name") && requestMap.containsKey("contactNumber")
                && requestMap.containsKey("email") && requestMap.containsKey("password");
    }

    @Override
    public ResponseEntity<String> login(Map<String, String> requestMap) {
        log.info("Inside login {}", requestMap.get("email"));

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(requestMap.get("email"), requestMap.get("password"))
            );

            if (auth.isAuthenticated()) {
                User loggedInUser = userDao.findByEmail(requestMap.get("email"));

                if (loggedInUser.getStatus().equalsIgnoreCase("true")) {
                    String token = jwtUtil.generateToken(loggedInUser.getEmail(), loggedInUser.getRole());
                    return ResponseEntity.ok("{\"token\":\"" + token + "\"}");
                } else {
                    return ResponseEntity.badRequest().body("{\"message\":\"Wait for Admin Approval.\"}");
                }
            }
        } catch (Exception ex) {
            log.error("Login failed for user {}: {}", requestMap.get("email"), ex.getMessage());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"message\":\"Bad credentials\"}");
    }

    @Override
    public ResponseEntity<List<UserWrapper>> getAllUser() {
        try {
            if (jwtFilter.isAdmin()) {
                return new ResponseEntity<>(userDao.getAllUser(), HttpStatus.OK);
            } else {
                return new ResponseEntity<>(new ArrayList<>(), HttpStatus.UNAUTHORIZED);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return new ResponseEntity<>(new ArrayList<>(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * @param requestMap
     * @return
     */
    @Override
    public ResponseEntity<String> update(Map<String, String> requestMap) {
        try {
            if (jwtFilter.isAdmin()) {
                Optional<User> optional = userDao.findById(requestMap.get("id"));
                if (optional.isPresent()) {
                    userDao.updateStatus(requestMap.get("status"), requestMap.get("id"));
                    sendMailToAllAdmin(requestMap.get("status"), optional.get().getEmail(), userDao.getAllAdmin());
                    return AutoUtils.getResponseEntity("User Status Successfully Updated", HttpStatus.OK);
                } else {
                    AutoUtils.getResponseEntity("User id does'nt exist", HttpStatus.OK);
                }
            } else {
                return AutoUtils.getResponseEntity(AutoConstants.UNAUTHORIZED_ACCESS, HttpStatus.UNAUTHORIZED);
            }


        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }


    private void sendMailToAllAdmin(String status, String user, List<String> allAdmin) {
        allAdmin.remove(jwtFilter.getCurrentUser());


        if (status != null && status.equalsIgnoreCase("true")) {
            emailUtils.sendSimpleMessage(jwtFilter.getCurrentUser(), "Account approved ", "USER:- " + user + "\n is approved by \n ADMIN:-" + jwtFilter.getCurrentUser(), allAdmin);

        } else {
            emailUtils.sendSimpleMessage(jwtFilter.getCurrentUser(), "Account Disabled ", "USER:- " + user + "\n is disabled by \n ADMIN:-" + jwtFilter.getCurrentUser(), allAdmin);

        }
    }

    @Override
    public ResponseEntity<String> checkToken() {

        return AutoUtils.getResponseEntity("true", HttpStatus.OK);

    }

    @Override
    public ResponseEntity<String> changePassword(Map<String, String> requestMap) {
        try{
            User userObj=userDao.findByEmail(jwtFilter.getCurrentUser());
            if(!userObj.equals(null)){

                if(passwordEncoder.matches(requestMap.get("oldPassword"), userObj.getPassword())){
                    userObj.setPassword(passwordEncoder.encode(requestMap.get("newPassword")));
                    userDao.save(userObj);
                    return AutoUtils.getResponseEntity("Password Updated Successfully", HttpStatus.OK);
                }return AutoUtils.getResponseEntity("Incorrect old password",HttpStatus.BAD_REQUEST);
            }return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
        }catch(Exception ex){
            ex.printStackTrace();
        }return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<String> forgotPassword(Map<String, String> requestMap) {
        try {
            String email = requestMap.get("email");

            if (email == null || email.isEmpty()) {
                return AutoUtils.getResponseEntity("Email is required", HttpStatus.BAD_REQUEST);
            }

            Optional<User> optionalUser = Optional.ofNullable(userDao.findByEmail(email));

            if (optionalUser.isEmpty()) {
                return AutoUtils.getResponseEntity(AutoConstants.EMAIL_DOES_NOT_EXIST, HttpStatus.NOT_FOUND);
            }

            User user = optionalUser.get();

            // Generate new random password
            String newPassword = User.RandomValueStringGenerator.generate(6);

            // Update encoded password
            user.setPassword(passwordEncoder.encode(newPassword));
            userDao.save(user); // Efficient JPA save

            // Send new password via email
            emailUtils.forgotMail(user.getEmail(), "Credentials by Cafe Management System", newPassword);

            return AutoUtils.getResponseEntity("Check your mail for credentials", HttpStatus.OK);
        } catch (Exception ex) {
            ex.printStackTrace();
            return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<UserWrapper> getCurrentUser() {
        try {
            // Get the current logged-in user's email using JWT filter
            String email = jwtFilter.getCurrentUser();
            User user = userDao.findByEmail(email);
            if (user != null) {
                UserWrapper wrapper = new UserWrapper(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getContactNumber(),
                        user.getStatus(),
                        user.getRole()
                );
                return new ResponseEntity<>(wrapper, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> uploadUserImage(String userId, MultipartFile file) {
        try {
            Optional<User> opt = userDao.findById(userId);
            if (opt.isEmpty()) {
                return AutoUtils.getResponseEntity("User not found", HttpStatus.NOT_FOUND);
            }
            User user = opt.get();
            byte[] bytes = file.getBytes();
            user.setUserImage(bytes);
            userDao.save(user);
            return AutoUtils.getResponseEntity("Image uploaded", HttpStatus.OK);
        } catch (IOException e) {
            log.error("Failed to read file bytes", e);
            return AutoUtils.getResponseEntity("Invalid file", HttpStatus.BAD_REQUEST);
        } catch (Exception ex) {
            log.error("Error uploading image", ex);
            return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


}
