package com.ledgerline.util;

import com.ledgerline.security.UserPrincipal;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    private final UserDetailsService userDetailsService;

    public SecurityUtils(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    public UserPrincipal getCurrentPrincipal(UserPrincipal principal) {
        if (principal != null) {
            return principal;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(authentication.getName());
            if (userDetails instanceof UserPrincipal up) {
                return up;
            }
        }
        throw new BadCredentialsException("Authentication required");
    }
}
