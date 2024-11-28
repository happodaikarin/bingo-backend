package com.granbuda.bingo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

@Document(collection = "users")
@Data
public class UserEntity {
    @Id
    private String id;
    private String username;
    private String email;
    private String password; // Almacenar la contraseña hasheada
}