package io.fairspace.saturn.controller.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TestController {

    private final TestInnerClass testInnerClass;

    @GetMapping("/test")
    public void testMethod() {
        testInnerClass.method();
    }

    @Component
    public static class TestInnerClass {
        public void method() {}
    }
}
