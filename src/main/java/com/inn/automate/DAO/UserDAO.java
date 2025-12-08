package com.inn.automate.DAO;


import com.inn.automate.POJO.User;
import com.inn.automate.wrapper.UserWrapper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UserDAO extends JpaRepository<User, String> {

    User findByEmailId(@Param("email")String email);
    List<UserWrapper> getAllUser();
    List<String> getAllAdmin();

    @Transactional
    @Modifying

    @Query
    Integer updateStatus(@Param("status")String status, @Param("id") String id);

    User findByEmail(String email);

    @Query(value = "SELECT id FROM users ORDER BY id DESC LIMIT 1", nativeQuery = true)
    String getLastUserId();

}
