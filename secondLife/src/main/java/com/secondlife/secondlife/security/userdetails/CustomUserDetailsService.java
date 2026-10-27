package com.secondlife.secondlife.security.userdetails;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.UserSecurityState;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailWithAuthoritiesIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
        return new CustomUserDetails(user);
    }

    @Transactional(readOnly = true)
    public CustomUserDetails loadUserById(UUID id) throws UsernameNotFoundException {
        User user = userRepository.findByIdWithAuthorities(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
        return new CustomUserDetails(user);
    }

    @Transactional(readOnly = true)
    public UserSecurityState loadSecurityState(UUID id) {
        return userRepository.findSecurityStateById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
    }
}
