package io.fairspace.saturn.controller;

import java.util.Collection;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserRolesUpdate;
import io.fairspace.saturn.services.users.UserService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/")
    public ResponseEntity<Collection<User>> getUsers() {
        return ResponseEntity.ok(userService.getUsers());
    }

    @PatchMapping("/")
    public ResponseEntity<Void> updateUserRoles(@RequestBody UserRolesUpdate update) {
        userService.update(update);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/current")
    public ResponseEntity<Object> getCurrentUser() {
        var currentUser = userService.currentUser();
        return ResponseEntity.ok(currentUser);
    }
}
