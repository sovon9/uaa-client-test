package com.sovon9.uaa_client_test.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/test")
    public String getData()
    {
        return "Successfully accessed site using Oauth2 public key validation";
    }

}
