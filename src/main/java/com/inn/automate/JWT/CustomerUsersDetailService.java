package com.inn.automate.JWT;


import com.inn.automate.DAO.UserDAO;
import com.inn.automate.POJO.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import org.springframework.security.core.userdetails.User;  // Importing Spring Security User class
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Objects;

@Slf4j
@Service
public class CustomerUsersDetailService implements UserDetailsService {

    @Autowired
    private UserDAO userDao;

    private User userDetail;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("Inside loadUserByUsername: {}", username);
        userDetail = userDao.findByEmailId(username);

        if (Objects.nonNull(userDetail)) {
            return new org.springframework.security.core.userdetails.User(
                    userDetail.getEmail(), userDetail.getPassword(), new ArrayList<>()
            );
        } else {
            throw new UsernameNotFoundException("User not found with email: " + username);
        }
    }

    public User getUserDetail() {
        return userDetail;
    }
}
