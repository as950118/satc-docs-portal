package com.docsportal.docs.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.docsportal.docs.config.CorsConfig;
import com.docsportal.docs.service.AuthService;

@WebMvcTest(controllers = AuthController.class, properties = "cors.allowed-origins=https://docs.example.com, http://localhost:3000")
@Import(CorsConfig.class)
class CorsPreflightTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    private ResultActions preflight(String origin) throws Exception {
        return mvc.perform(options("/api/v1/auth/login")
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "POST"));
    }

    @Test
    void configuredOriginsAreAllowed() throws Exception {
        preflight("https://docs.example.com")
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://docs.example.com"));
        preflight("http://localhost:3000")
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void otherOriginIsRejected() throws Exception {
        preflight("http://evil.example")
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
