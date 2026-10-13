package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.response.TokenResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.mapper.UserMapper;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.*;
import com.secondlife.secondlife.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistrationGenderApiTest {
    private final UserRepository users=mock(UserRepository.class);
    private final RoleRepository roles=mock(RoleRepository.class);
    private final TokenService tokens=mock(TokenService.class);
    private final NotificationService notifications=mock(NotificationService.class);
    private final UserMapper mapper=new UserMapper();
    private MockMvc mvc;
    private User saved;
    @BeforeEach void setup() {
        when(roles.findByCodeWithPermissions("BUYER")).thenReturn(Optional.of(new Role("BUYER","Buyer","Buyer")));
        when(users.save(any())).thenAnswer(i -> { saved=i.getArgument(0); saved.setId(UUID.randomUUID()); return saved; });
        when(tokens.createEmailVerificationOtp(any())).thenReturn("123456");
        when(tokens.generateTokenPair(any())).thenReturn(new TokenResponse("access","refresh",1800000));
        var service=new AuthServiceImpl(users,roles,new BCryptPasswordEncoder(),mock(AuthenticationManager.class),tokens,notifications,mapper,mock(GoogleAuthService.class));
        mvc=MockMvcBuilders.standaloneSetup(new AuthController(service,mock(UserService.class),mock(com.secondlife.secondlife.security.CurrentUserProvider.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    private String request(String genderField) {
        return "{\"email\":\"gender@example.test\",\"password\":\"Password@123\",\"fullName\":\"Nguyễn Test\",\"phone\":\"0912345678\""+genderField+"}";
    }
    @ParameterizedTest @ValueSource(strings={"MALE","FEMALE","OTHER"})
    void selectedGenderIsSavedAndReturnedWithTheRegistration(String gender) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request(",\"gender\":\""+gender+"\"")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.user.gender").value(gender))
                .andExpect(jsonPath("$.data.user.password").doesNotExist()).andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
        assertEquals(gender,saved.getProfile().getGender().name());
        assertTrue(saved.hasRole("BUYER")); assertEquals(AccountStatus.ACTIVE,saved.getAccountStatus());
    }
    @ParameterizedTest @ValueSource(strings={"\"INVALID\"","\"male\"","0","true","{}","[]"})
    void invalidGenderIsRejectedBeforeSavingOrSendingEmail(String gender) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request(",\"gender\":"+gender)))
                .andExpect(status().isBadRequest());
        verify(users,never()).save(any()); verifyNoInteractions(notifications,tokens);
    }
    @Test void anOmittedGenderRemainsUnspecified() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request("")))
                .andExpect(status().isCreated());
        assertNull(saved.getProfile().getGender());
    }
}
