package io.fairspace.saturn.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserRolesUpdate;
import io.fairspace.saturn.services.users.UserService;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService service;

    @Test
    void testGetUsers() throws Exception {
        var user1 = createTestUser("1", "User One", "user1@example.com", "user1", true, false);
        var user2 = createTestUser("2", "User Two", "user2@example.com", "user2", false, true);
        var users = List.of(user1, user2);
        when(service.getUsers()).thenReturn(users);

        mockMvc.perform(get("/users/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // Expect 200 OK
                .andExpect(
                        content()
                                .json(
                                        """
                    [
                        {
                            "id": "1",
                            "name": "User One",
                            "email": "user1@example.com",
                            "username": "user1",
                            "isSuperadmin": true,
                            "isAdmin": false,
                            "canViewPublicMetadata": true,
                            "canViewPublicData": false,
                            "canAddSharedMetadata": true,
                            "canQueryMetadata": true
                        },
                        {
                            "id": "2",
                            "name": "User Two",
                            "email": "user2@example.com",
                            "username": "user2",
                            "isSuperadmin": false,
                            "isAdmin": true,
                            "canViewPublicMetadata": true,
                            "canViewPublicData": false,
                            "canAddSharedMetadata": true,
                            "canQueryMetadata": true
                        }
                    ]
                    """));
    }

    @Test
    void testUpdateUserRoles() throws Exception {
        UserRolesUpdate update = new UserRolesUpdate();
        update.setId("1");
        update.setAdmin(true);
        update.setCanViewPublicMetadata(true);
        update.setCanViewPublicData(false);
        update.setCanAddSharedMetadata(true);
        update.setCanQueryMetadata(false);

        doNothing().when(service).update(update);

        mockMvc.perform(
                        patch("/users/")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                            "id": "1",
                            "isAdmin": true,
                            "canViewPublicMetadata": true,
                            "canViewPublicData": false,
                            "canAddSharedMetadata": true,
                            "canQueryMetadata": false
                        }
                    """))
                .andExpect(status().isNoContent());
    }

    @Test
    void testGetCurrentUser() throws Exception {
        var currentUser = createTestUser("1", "Current User", "currentuser@example.com", "currentuser", true, true);

        when(service.currentUser()).thenReturn(currentUser);

        mockMvc.perform(get("/users/current").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // Expect 200 OK
                .andExpect(
                        content()
                                .json(
                                        """
                    {
                        "id": "1",
                        "name": "Current User",
                        "email": "currentuser@example.com",
                        "username": "currentuser",
                        "isSuperadmin": true,
                        "isAdmin": true,
                        "canViewPublicMetadata": true,
                        "canViewPublicData": false,
                        "canAddSharedMetadata": true,
                        "canQueryMetadata": true
                    }
                    """));
    }

    private User createTestUser(
            String id, String name, String email, String username, boolean superadmin, boolean admin) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setUsername(username);
        user.setSuperadmin(superadmin);
        user.setAdmin(admin);
        user.setCanViewPublicMetadata(true);
        user.setCanViewPublicData(false);
        user.setCanAddSharedMetadata(true);
        user.setCanQueryMetadata(true);
        return user;
    }
}
