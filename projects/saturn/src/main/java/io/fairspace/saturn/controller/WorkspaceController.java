package io.fairspace.saturn.controller;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.fairspace.saturn.services.workspaces.UserRoleDto;
import io.fairspace.saturn.services.workspaces.Workspace;
import io.fairspace.saturn.services.workspaces.WorkspaceRole;
import io.fairspace.saturn.services.workspaces.WorkspaceService;

@RestController
@RequestMapping("/workspaces")
@Validated
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PutMapping("/")
    public ResponseEntity<Workspace> createWorkspace(@RequestBody Workspace workspace) {
        var createdWorkspace = workspaceService.createWorkspace(workspace);
        return ResponseEntity.ok(
                createdWorkspace); // it should return HTTP 201 CREATED - tobe analyzed across the codebase
    }

    @GetMapping("/")
    public ResponseEntity<List<Workspace>> listWorkspaces() {
        var workspaces = workspaceService.listWorkspaces();
        return ResponseEntity.ok(workspaces);
    }

    @DeleteMapping("/")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkspace(@RequestParam("workspace") String workspaceUri) {
        workspaceService.deleteWorkspace(NodeFactory.createURI(workspaceUri));
    }

    @GetMapping(value = "/users/")
    public ResponseEntity<Map<Node, WorkspaceRole>> getUsers(@RequestParam("workspace") String workspaceUri) {
        var uri = NodeFactory.createURI(workspaceUri);
        var users = workspaceService.getUsers(uri);
        return ResponseEntity.ok(users);
    }

    @PatchMapping(value = "/users/")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setUserRole(@RequestBody UserRoleDto userRoleDto) {
        workspaceService.setUserRole(userRoleDto.getWorkspace(), userRoleDto.getUser(), userRoleDto.getRole());
    }
}
