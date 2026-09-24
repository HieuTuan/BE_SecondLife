package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Post> {
}
