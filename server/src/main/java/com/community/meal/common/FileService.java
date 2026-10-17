package com.community.meal.common;

import static com.community.meal.common.Values.*;

import com.community.meal.auth.*;
import com.community.meal.contracts.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
  private final Db db;
  private final PrivateFileStorage storage;
  private final Access access;
  private final FileReadAccess fileAccess;

  public FileService(Db db, Access access, PrivateFileStorage storage, FileReadAccess fileAccess) {
    this.db = db;
    this.access = access;
    this.storage = storage;
    this.fileAccess = fileAccess;
  }

  @Transactional
  public Map<String, Object> upload(AuthPrincipal p, String purpose, MultipartFile file) {
    choice(purpose, "BINDING", "SUBSIDY", "DISH_IMAGE", "REVIEW", "TICKET");
    if (purpose.equals("DISH_IMAGE")) access.permit(p, "dishes:write", "ADMIN");
    else if (List.of("REVIEW", "TICKET").contains(purpose)) access.role(p, "FAMILY");
    else access.role(p, "FAMILY", "COMMUNITY");
    if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
      throw ApiException.invalid("附件大小应为 1 字节到 5MB");
    String name = str(file.getOriginalFilename());
    if (name.contains("/") || name.contains("\\") || name.contains(".."))
      throw ApiException.invalid("文件名无效");
    try {
      byte[] data = file.getBytes();
      String mime = str(file.getContentType()), extension = null;
      String lower = name.toLowerCase(Locale.ROOT);
      if (mime.equals("image/png")
          && data.length > 8
          && Arrays.equals(
              Arrays.copyOf(data, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10})
          && lower.endsWith(".png")) extension = "png";
      if (mime.equals("image/jpeg")
          && data.length > 3
          && data[0] == (byte) 255
          && data[1] == (byte) 216
          && (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) extension = "jpg";
      if (mime.equals("image/webp")
          && data.length > 12
          && new String(data, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
          && new String(data, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")
          && lower.endsWith(".webp")) extension = "webp";
      if (List.of("BINDING", "SUBSIDY").contains(purpose)
          && mime.equals("application/pdf")
          && data.length > 5
          && new String(data, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")
          && lower.endsWith(".pdf")) extension = "pdf";
      if (extension == null)
        throw ApiException.invalid("附件格式或内容不符合要求，仅支持 PNG/JPEG/WebP，资格材料另支持 PDF");
      String relative = storage.store(extension, data);
      if (TransactionSynchronizationManager.isSynchronizationActive())
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED)
                  try {
                    storage.delete(relative);
                  } catch (Exception ignored) {
                  }
              }
            });
      long id =
          db.insert(
              "INSERT INTO stored_file(owner_user_id,purpose,storage_path,content_type,size_bytes)"
                  + " VALUES(?,?,?,?,?)",
              p.userId(),
              purpose,
              relative,
              mime,
              data.length);
      db.audit(p, "FILE_UPLOAD", "FILE", id);
      return map("id", id, "contentType", mime, "sizeBytes", data.length);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("附件存储失败");
    }
  }

  public Map<String, Object> metadata(AuthPrincipal p, long id) {
    var file = db.one("SELECT * FROM stored_file WHERE id=?", id);
    if (num(file.get("ownerUserId")) == p.userId()) return file;
    boolean allowed = fileAccess.mayRead(p, id, str(file.get("purpose")));
    if (!allowed) throw ApiException.forbidden();
    return file;
  }

  public byte[] bytes(Map<String, Object> file) {
    return storage.read(str(file.get("storagePath")));
  }
}
