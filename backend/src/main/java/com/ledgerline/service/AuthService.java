package com.ledgerline.service;

import com.ledgerline.dto.AuthRequest;
import com.ledgerline.dto.AuthResponse;
import com.ledgerline.dto.RegisterRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.User;
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
            throw new InvalidTransactionException(
                    "Username is already taken: " + request.getUsername());
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new InvalidTransactionException(
                    "Email is already registered: " + request.getEmail());
        }

        // All public registrations are account holders with PENDING status.
        User user = new User(
                request.getUsername(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName(),
                Role.ROLE_ACCOUNT_HOLDER
        );

        User savedUser = userRepository.save(user);

        // Preserve default checking account creation.
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
                "User registered and is waiting for admin approval"
        );

        // Do not generate a JWT until the administrator approves the account.
        return new AuthResponse(
                null,
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
                    new UsernamePasswordAuthenticationToken(
                            request.getUsernameOrEmail(),
                            request.getPassword()
                    )
            );

            UserPrincipal userPrincipal =
                    (UserPrincipal) authentication.getPrincipal();

            User user = userRepository.findByUsername(userPrincipal.getUsername())
                    .orElseThrow(() ->
                            new IllegalArgumentException("User account not found"));

            String status = user.getStatus();

            // Block accounts waiting for administrator approval.
            if ("PENDING".equalsIgnoreCase(status)) {
                auditLogService.logEvent(
                        AuditEventType.LOGIN_FAILURE,
                        user.getUsername(),
                        clientIp,
                        null,
                        null,
                        "FAILURE",
                        "Account pending approval",
                        "Login blocked because admin approval is pending"
                );

                throw new IllegalArgumentException(
                        "Your account is waiting for admin approval."
                );
            }

            // Block rejected accounts.
            if ("REJECTED".equalsIgnoreCase(status)) {
                auditLogService.logEvent(
                        AuditEventType.LOGIN_FAILURE,
                        user.getUsername(),
                        clientIp,
                        null,
                        null,
                        "FAILURE",
                        "Account rejected",
                        "Login blocked because account was rejected"
                );

                throw new IllegalArgumentException(
                        "Your registration has been rejected. Please contact the administrator."
                );
            }

            // Only ACTIVE and APPROVED accounts can log in.
            if (!"ACTIVE".equalsIgnoreCase(status)
                    && !"APPROVED".equalsIgnoreCase(status)) {
                throw new IllegalArgumentException(
                        "Your account is not active. Please contact the administrator."
                );
            }

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

            throw new BadCredentialsException(
                    "Invalid username or password"
            );
        }
    }
}
