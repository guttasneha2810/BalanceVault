package com.ledgerline;

import com.ledgerline.entity.Account;
import com.ledgerline.entity.User;
import com.ledgerline.enums.AccountStatus;
import com.ledgerline.enums.AccountType;
import com.ledgerline.enums.Role;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.AuditLogRepository;
import com.ledgerline.repository.IdempotencyRecordRepository;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.repository.LedgerTransactionRepository;
import com.ledgerline.repository.UserRepository;
import com.ledgerline.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected AccountRepository accountRepository;

    @Autowired
    protected LedgerTransactionRepository transactionRepository;

    @Autowired
    protected LedgerEntryRepository entryRepository;

    @Autowired
    protected IdempotencyRecordRepository idempotencyRepository;

    @Autowired
    protected AuditLogRepository auditLogRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected User adminUser;
    protected User userOne;
    protected User userTwo;
    protected User auditorUser;

    protected UserPrincipal userOnePrincipal;
    protected UserPrincipal userTwoPrincipal;
    protected UserPrincipal auditorPrincipal;
    protected UserPrincipal adminPrincipal;

    @BeforeEach
    public void setupBaseData() {
        auditLogRepository.deleteAllInBatch();
        idempotencyRepository.deleteAllInBatch();
        entryRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();


        // 1. Create Users
        adminUser = userRepository.save(new User("admin", "admin@ledgerline.com", passwordEncoder.encode("Admin@12345"), "Admin User", Role.ROLE_ADMIN));
        userOne = userRepository.save(new User("user1", "user1@ledgerline.com", passwordEncoder.encode("User@12345"), "User One", Role.ROLE_ACCOUNT_HOLDER));
        userTwo = userRepository.save(new User("user2", "user2@ledgerline.com", passwordEncoder.encode("User@12345"), "User Two", Role.ROLE_ACCOUNT_HOLDER));
        auditorUser = userRepository.save(new User("auditor", "auditor@ledgerline.com", passwordEncoder.encode("Auditor@12345"), "Auditor User", Role.ROLE_AUDITOR));

        userOnePrincipal = UserPrincipal.create(userOne);
        userTwoPrincipal = UserPrincipal.create(userTwo);
        auditorPrincipal = UserPrincipal.create(auditorUser);
        adminPrincipal = UserPrincipal.create(adminUser);

        // 2. Create System Clearing Accounts
        Account depClearing = new Account();
        depClearing.setAccountNumber("SYS-EXTERNAL-DEP-001");
        depClearing.setAccountName("System Deposit Clearing");
        depClearing.setAccountType(AccountType.SYSTEM_CLEARING);
        depClearing.setBalance(BigDecimal.ZERO);
        depClearing.setCurrency("USD");
        depClearing.setStatus(AccountStatus.ACTIVE);
        depClearing.setSystemAccount(true);
        accountRepository.save(depClearing);

        Account wthClearing = new Account();
        wthClearing.setAccountNumber("SYS-EXTERNAL-WTH-001");
        wthClearing.setAccountName("System Withdrawal Clearing");
        wthClearing.setAccountType(AccountType.SYSTEM_CLEARING);
        wthClearing.setBalance(BigDecimal.ZERO);
        wthClearing.setCurrency("USD");
        wthClearing.setStatus(AccountStatus.ACTIVE);
        wthClearing.setSystemAccount(true);
        accountRepository.save(wthClearing);
    }

    protected Account createTestAccount(User owner, String accountNumber, BigDecimal initialBalance) {
        Account account = new Account(
                accountNumber != null ? accountNumber : "ACC-" + UUID.randomUUID().toString().substring(0, 8),
                owner,
                owner.getFullName() + " Account",
                AccountType.CHECKING,
                initialBalance,
                "USD"
        );
        return accountRepository.save(account);
    }
}
