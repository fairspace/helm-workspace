package io.fairspace.saturn.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import io.fairspace.saturn.services.maintenance.MaintenanceService;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MaintenanceController.class)
class MaintenanceControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MaintenanceService maintenanceService;

    @Test
    void testStartReindex() throws Exception {
        doNothing().when(maintenanceService).startRecreateIndexTask();

        mockMvc.perform(post("/maintenance/reindex").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent()); // Expect 204 No Content
        verify(maintenanceService).startRecreateIndexTask();
    }

    @Test
    void testCompactRdfStorage() throws Exception {
        doNothing().when(maintenanceService).compactRdfStorageTask();

        mockMvc.perform(post("/maintenance/compact").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent()); // Expect 204 No Content
        verify(maintenanceService).compactRdfStorageTask();
    }

    @Test
    void testGetStatusActive() throws Exception {
        when(maintenanceService.active()).thenReturn(true);

        mockMvc.perform(get("/maintenance/status").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk()) // Expect 200 OK
                .andExpect(content().string("active")); // Expect content "active"
        verify(maintenanceService).active();
    }

    @Test
    void testGetStatusInactive() throws Exception {
        when(maintenanceService.active()).thenReturn(false);

        mockMvc.perform(get("/maintenance/status").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk()) // Expect 200 OK
                .andExpect(content().string("inactive")); // Expect content "inactive"
        verify(maintenanceService).active();
    }
}
