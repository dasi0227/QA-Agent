package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.types.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/function")
public class FunctionController {

    @GetMapping("/llm/health")
    public Result<Void> health() {
        return Result.success();
    }
}
