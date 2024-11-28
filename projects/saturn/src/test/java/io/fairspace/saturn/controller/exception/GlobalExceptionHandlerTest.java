package io.fairspace.saturn.controller.exception;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import io.fairspace.saturn.controller.BaseControllerTest;
import io.fairspace.saturn.services.metadata.validation.ValidationException;
import io.fairspace.saturn.services.metadata.validation.Violation;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({GlobalExceptionHandler.class, TestController.class})
public class GlobalExceptionHandlerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TestController.TestInnerClass testInnerClass;

    @Test
    public void testHandleConstraintViolationException() throws Exception {
        // Mocking a ConstraintViolationException with a couple of violations
        ConstraintViolation<?> violation1 = Mockito.mock(ConstraintViolation.class);
        ConstraintViolation<?> violation2 = Mockito.mock(ConstraintViolation.class);
        when(violation1.getMessage()).thenReturn("Violation 1");
        when(violation2.getMessage()).thenReturn("Violation 2");
        Set<ConstraintViolation<?>> violations = Set.of(violation1, violation2);
        ConstraintViolationException exception = new ConstraintViolationException(violations);

        doThrow(exception).when(testInnerClass).method(); // Simulating the exception

        mockMvc.perform(get("/test"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(
                        content()
                                .json(
                                        """
                    {
                        "status": 400,
                        "message": "Validation Error",
                        "details": "Violations: Violation 1; Violation 2"
                    }
                """));
    }

    @Test
    public void testHandleValidationException() throws Exception {
        // Mocking a ValidationException with a violation
        Set<Violation> violations = Set.of(new Violation("Invalid value", "subject", "predicate", "value"));
        ValidationException exception = new ValidationException(violations);

        doThrow(exception).when(testInnerClass).method(); // Simulating the exception

        mockMvc.perform(get("/test"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(
                        content()
                                .json(
                                        """
                    {
                        "status": 400,
                        "message": "Validation Error",
                        "details": [
                            {
                                "message": "Invalid value",
                                "subject": "subject",
                                "predicate": "predicate",
                                "value": "value"
                            }
                        ]
                    }
                """));
    }

    @Test
    public void testHandleIllegalArgumentException() throws Exception {
        // Mocking an IllegalArgumentException
        IllegalArgumentException exception = new IllegalArgumentException("Invalid argument");

        doThrow(exception).when(testInnerClass).method(); // Simulating the exception

        mockMvc.perform(get("/test"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(
                        content()
                                .json(
                                        """
                    {
                        "status": 400,
                        "message": "Validation Error",
                        "details": "Invalid argument"
                    }
                """));
    }

    @Test
    public void testHandleAccessDeniedException() throws Exception {
        // Mocking an AccessDeniedException
        AccessDeniedException exception = new AccessDeniedException("Access denied");

        doThrow(exception).when(testInnerClass).method(); // Simulating the exception

        mockMvc.perform(get("/test"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(
                        content()
                                .json(
                                        """
                    {
                        "status": 403,
                        "message": "Access Denied",
                        "details": null
                    }
                """));
    }
}
