package com.example.htmlviewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createAndFetchDocument() throws Exception {
        String createResponse = mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "alice")
                        .content("""
                                {"name":"Landing","html":"<div><h1>Hello</h1></div>"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(createResponse);
        String id = json.get("id").asText();

        mockMvc.perform(get("/api/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Landing"));
    }

    @Test
    void saveWithConflictReturns409() throws Exception {
        String createResponse = mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "alice")
                        .content("""
                                {"name":"A","html":"<div>v1</div>"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(put("/api/documents/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "alice")
                        .content("""
                                {"baseVersion":1,"html":"<div>v2</div>","clientId":"a"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newVersion").value(2));

        mockMvc.perform(put("/api/documents/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "bob")
                        .content("""
                                {"baseVersion":1,"html":"<div>stale</div>","clientId":"b"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.currentVersion").value(2));
    }

    @Test
    void sanitizesUnsafeScriptTags() throws Exception {
        String createResponse = mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "alice")
                        .content("""
                                {"name":"Unsafe","html":"<div>ok</div><script>alert('x')</script>"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(createResponse);
        String sanitized = node.get("sanitizedHtml").asText();
        assertThat(sanitized).doesNotContain("<script>");
    }
}
