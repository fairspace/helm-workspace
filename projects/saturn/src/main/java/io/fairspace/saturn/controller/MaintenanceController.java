package io.fairspace.saturn.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.fairspace.saturn.services.maintenance.MaintenanceService;

@RestController
@RequestMapping("/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    @PostMapping("/reindex")
    public ResponseEntity<Void> startReindex() {
        maintenanceService.startRecreateIndexTask();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/compact")
    public ResponseEntity<Void> compactRdfStorage() {
        maintenanceService.compactRdfStorageTask();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        var status = maintenanceService.active() ? "active" : "inactive";
        return ResponseEntity.ok(status);
    }
}
