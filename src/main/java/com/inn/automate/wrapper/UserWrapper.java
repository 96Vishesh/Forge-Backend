package com.inn.automate.wrapper;


import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserWrapper {

//    UserWrapper user=new UserWrapper();

    private String id;

    private String name;

    private String email;

    private String contactNumber;

    private String status;

    private String role;

    public UserWrapper(String id, String name, String email, String contactNumber, String status) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.contactNumber = contactNumber;
        this.status = status;
    }
    public UserWrapper(String id, String name, String email, String contactNumber, String status, String role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.contactNumber = contactNumber;
        this.status = status;
        this.role=role;
    }
}
