package com.community.meal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileBusinessTest extends BusinessTestSupport {
  @Test
  void privateFilesValidateContentAndCannotBeReadByOtherUsers() throws Exception {
    var valid =
        new MockMultipartFile("file", "proof.pdf", "application/pdf", "%PDF-1.4\nTEST".getBytes());
    String response =
        mvc.perform(
                multipart("/api/v1/files/upload")
                    .file(valid)
                    .param("purpose", "BINDING")
                    .header("Authorization", "Bearer " + tokens.get("family")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = json.readTree(response).path("data").path("id").asLong();
    mvc.perform(
            get("/api/v1/files/" + id).header("Authorization", "Bearer " + tokens.get("family")))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/files/" + id).header("Authorization", "Bearer " + tokens.get("canteen")))
        .andExpect(status().isForbidden());
    long elder =
        call("POST", "/elders", Map.of("name", "材料核验老人", "siteId", 1), "community", 200)
            .path("id")
            .asLong();
    call("POST", "/family/bindings", Map.of("elderId", elder, "proofFileId", id), "family", 200);
    mvc.perform(
            get("/api/v1/files/" + id).header("Authorization", "Bearer " + tokens.get("community")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/files/" + id).header("Authorization", "Bearer " + tokens.get("admin")))
        .andExpect(status().isForbidden());
    mvc.perform(
            multipart("/api/v1/files/upload")
                .file(valid)
                .param("purpose", "DISH_IMAGE")
                .header("Authorization", "Bearer " + tokens.get("family")))
        .andExpect(status().isForbidden());
    for (var file :
        new MockMultipartFile[] {
          new MockMultipartFile("file", "bad.png", "image/png", "<script>x</script>".getBytes()),
          new MockMultipartFile(
              "file", "../proof.pdf", "application/pdf", "%PDF-1.4\nTEST".getBytes()),
          new MockMultipartFile(
              "file", "large.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1])
        })
      mvc.perform(
              multipart("/api/v1/files/upload")
                  .file(file)
                  .param("purpose", "BINDING")
                  .header("Authorization", "Bearer " + tokens.get("family")))
          .andExpect(status().isBadRequest());
  }
}
