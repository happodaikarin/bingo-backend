package com.granbuda.bingo.repository;


import org.springframework.data.mongodb.repository.MongoRepository;

import com.granbuda.bingo.model.UserEntity;

import java.util.Optional;

public interface UserRepository extends MongoRepository<UserEntity, String> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByUsername(String username);
}