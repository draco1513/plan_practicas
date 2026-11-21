package com.esfx.taskflow.access;

import com.esfx.taskflow.common.UserDirectory;
import com.esfx.taskflow.common.UserSummary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class UserDirectoryImpl implements UserDirectory {
    private final UserRepository users;

    public UserDirectoryImpl(UserRepository users) { this.users = users; }

    @Override
    public Optional<UserSummary> find(long userId) {
        return users.findById(userId).map(u -> new UserSummary(u.id(), u.fullName(), u.email()));
    }

    @Override
    public Map<Long, UserSummary> findAll(Collection<Long> userIds) {
        Map<Long, UserSummary> map = new HashMap<>();
        for (UserRecord u : users.findByIds(userIds.stream().distinct().toList())) {
            map.put(u.id(), new UserSummary(u.id(), u.fullName(), u.email()));
        }
        return map;
    }

    @Override
    public boolean existsActive(long userId) {
        return users.findById(userId).map(UserRecord::active).orElse(false);
    }
}
