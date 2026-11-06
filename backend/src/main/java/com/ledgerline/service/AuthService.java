package com.ledgerline.service;

import com.ledgerline.dto.AuthRequest;
import com.ledgerline.dto.AuthResponse;
import com.ledgerline.dto.RegisterRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.User;
import com.ledgerline.enums.AccountStatus;
import com.ledgerline.enums.AccountType;
import com.ledgerline.enums.AuditEventType;
import com.ledgerline.enums.Role;
import com.ledgerline.exception.InvalidTransactionException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.UserRepository;
import com.ledgerline.security.JwtTokenProvider;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.util.AccountNumberGenerator;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final AuditLogService auditLogService;

    public AuthService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenProvider tokenProvider,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new InvalidTransactionException("Username is already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new InvalidTransactionException("Email is already registered: " + request.getEmail());
        }

        // STRICT SECURITY: Never trust role from frontend. Registration is strictly ROLE_ACCOUNT_HOLDER.
        User user = new User(
                request.getUsername(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName(),
                Role.ROLE_ACCOUNT_HOLDER
        );
        User savedUser = userRepository.save(user);

        // Provision default checking account for user
        Account defaultAccount = new Account(
                AccountNumberGenerator.generate(),
                savedUser,
                savedUser.getFullName() + " Checking",
                AccountType.CHECKING,
                BigDecimal.ZERO,
                "USD"
        );
        accountRepository.save(defaultAccount);

        auditLogService.logEvent(
                AuditEventType.REGISTRATION,
                savedUser.getUsername(),
                clientIp,
                defaultAccount.getAccountNumber(),
                null,
                "SUCCESS",
                null,
                "User registered with default checking account"
        );

        // Auto authenticate and generate token
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        String token = tokenProvider.generateToken(authentication);

        return new AuthResponse(
                token,
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getRole()
        );
    }

    @Transactional
    public AuthResponse login(AuthRequest request, String clientIp) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword())
            );

            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            String token = tokenProvider.generateToken(authentication);

            auditLogService.logEvent(
                    AuditEventType.LOGIN_SUCCESS,
                    userPrincipal.getUsername(),
                    clientIp,
                    null,
                    null,
                    "SUCCESS",
                    null,
                    "Successful authentication"
            );

            return new AuthResponse(
                    token,
                    userPrincipal.getId(),
                    userPrincipal.getUsername(),
                    userPrincipal.getEmail(),
                    userPrincipal.getFullName(),
                    userPrincipal.getRole()
            );

        } catch (AuthenticationException ex) {
            auditLogService.logEvent(
                    AuditEventType.LOGIN_FAILURE,
                    request.getUsernameOrEmail(),
                    clientIp,
                    null,
                    null,
                    "FAILURE",
                    "Invalid credentials",
                    "Failed login attempt"
            );
            throw new BadCredentialsException("Invalid username or password");
        }
    }
}
