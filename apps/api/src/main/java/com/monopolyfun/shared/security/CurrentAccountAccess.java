package com.monopolyfun.shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Component
public class CurrentAccountAccess {
    public Optional<CurrentAccount> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentAccount currentAccount)) {
            return Optional.empty();
        }
        return Optional.of(currentAccount);
    }

    public String requireAccountId() {
        return current()
                .map(CurrentAccount::accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    public void requireSameAccount(String actorAccountId) {
        String currentAccountId = requireAccountId();
        if (!currentAccountId.equals(actorAccountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Request actor must match authenticated account");
        }
    }
}
