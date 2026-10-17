package com.community.meal.common;

import com.community.meal.auth.AuthPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
  private final FileService files;

  public FileController(FileService files) {
    this.files = files;
  }

  @PostMapping("/upload")
  public ApiResponse<?> upload(
      @AuthenticationPrincipal AuthPrincipal p,
      @RequestParam String purpose,
      @RequestParam MultipartFile file) {
    return ApiResponse.ok(files.upload(p, purpose, file));
  }

  @GetMapping("/{id}")
  public ResponseEntity<byte[]> get(
      @AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
    var file = files.metadata(p, id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.get("contentType").toString()))
        .header("Content-Disposition", "attachment; filename=attachment-" + id)
        .header("Cache-Control", "no-store")
        .body(files.bytes(file));
  }
}
