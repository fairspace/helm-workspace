package io.fairspace.saturn.controller;

import java.util.List;
import java.util.Map;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import io.fairspace.saturn.services.workspaces.Workspace;
import io.fairspace.saturn.services.workspaces.WorkspaceRole;
import io.fairspace.saturn.services.workspaces.WorkspaceService;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkspaceController.class)
class WorkspaceControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkspaceService workspaceService;

    @Test
    void createWorkspace_shouldReturnCreatedWorkspace() throws Exception {
        Workspace workspace = new Workspace();
        workspace.setCode("WS001");

        when(workspaceService.createWorkspace(any(Workspace.class))).thenReturn(workspace);

        mockMvc.perform(put("/workspaces/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"WS001\", \"title\": \"New Workspace\"}"))
                .andExpect(status().isOk()) // Use isCreated() if HTTP 201 Created is implemented
                .andExpect(jsonPath("$.code").value("WS001"));
    }

    @Test
    void listWorkspaces_shouldReturnListOfWorkspaces() throws Exception {
        Workspace workspace = new Workspace();
        workspace.setCode("WS001");

        when(workspaceService.listWorkspaces()).thenReturn(List.of(workspace));

        mockMvc.perform(get("/workspaces/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("WS001"));
    }

    @Test
    void deleteWorkspace_shouldDeleteWorkspace() throws Exception {
        String workspaceUri = "http://example.com/workspace/1";

        mockMvc.perform(delete("/workspaces/").param("workspace", workspaceUri)).andExpect(status().isNoContent());

        Mockito.verify(workspaceService).deleteWorkspace(NodeFactory.createURI(workspaceUri));
    }

    @Test
    void getUsers_shouldReturnWorkspaceUsers() throws Exception {
        String workspaceUri = "http://example.com/workspace/1";
        var users = Map.of(NodeFactory.createURI("http://example.com/user/1"), WorkspaceRole.Member);

        when(workspaceService.getUsers(any())).thenReturn(users);

        mockMvc.perform(get("/workspaces/users/").param("workspace", workspaceUri))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['http://example.com/user/1']").value("Member"));
    }

    @Test
    void setUserRole_shouldUpdateUserRole() throws Exception {
        mockMvc.perform(
                        patch("/workspaces/users/")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"workspace\": \"http://example.com/workspace/1\", \"user\": \"http://example.com/user/1\", \"role\": \"Manager\"}"))
                .andExpect(status().isNoContent());

        // Use ArgumentCaptor to capture the arguments passed to the method
        ArgumentCaptor<Node> workspaceCaptor = ArgumentCaptor.forClass(Node.class);
        ArgumentCaptor<Node> userCaptor = ArgumentCaptor.forClass(Node.class);
        ArgumentCaptor<WorkspaceRole> roleCaptor = ArgumentCaptor.forClass(WorkspaceRole.class);

        // Verify that setUserRole was called once and capture the arguments
        Mockito.verify(workspaceService, times(1))
                .setUserRole(workspaceCaptor.capture(), userCaptor.capture(), roleCaptor.capture());

        // Now you can assert that the captured arguments are what you expect
        assertEquals(NodeFactory.createURI("http://example.com/workspace/1"), workspaceCaptor.getValue());
        assertEquals(NodeFactory.createURI("http://example.com/user/1"), userCaptor.getValue());
        assertEquals(WorkspaceRole.Manager, roleCaptor.getValue()); // Make sure the role is Manager
    }
}
