package com.community.meal.elder;

import static com.community.meal.common.Values.*;

import com.community.meal.auth.Access;
import com.community.meal.common.*;
import com.community.meal.contracts.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ElderService implements ElderProfiles {
  private final Db db;
  private final Access access;
  private final Crypto crypto;
  private final CatalogSnapshots catalog;

  public ElderService(Db db, Access access, Crypto crypto, CatalogSnapshots catalog) {
    this.db = db;
    this.access = access;
    this.crypto = crypto;
    this.catalog = catalog;
  }

  public Map<String, Object> row(long id) {
    return db.one("SELECT * FROM elder WHERE id=?", id);
  }

  public Map<String, Object> rowForUpdate(long id) {
    return db.one("SELECT * FROM elder WHERE id=? FOR UPDATE", id);
  }

  public boolean subsidized(long id) {
    return subsidized(id,java.time.LocalDate.now(BusinessTime.ZONE));
  }
  public boolean subsidized(long id,java.time.LocalDate mealDate){
    return db.count("SELECT COUNT(*) FROM subsidy_entitlement WHERE elder_id=? AND is_active=1 AND (valid_from IS NULL OR valid_from<=?) AND (valid_to IS NULL OR valid_to>=?)",id,mealDate,mealDate)>0;
  }

  public Map<String, Object> dietarySummary(long id) {
    var health =
        db.optional(
            "SELECT avoid_ingredients_json,allergen_ids_json FROM elder_health WHERE elder_id=?",
            id);
    return health == null
        ? map("notice", "饮食资料待核对")
        : map(
            "avoidIngredients",
            health.get("avoidIngredients"),
            "allergenNames",
            catalog.allergenNames(ids(health.get("allergenIds"))));
  }

  public List<Map<String, Object>> serviceSites() {
    return db.list(
        "SELECT id,name,address,phone FROM service_site WHERE business_status='OPEN' ORDER BY id");
  }

  private Map<String, Object> safe(Map<String, Object> elder) {
    var result = new LinkedHashMap<>(elder);
    result.remove("identityCipher");
    String phone = str(result.remove("phone"));
    result.put(
        "phoneMasked",
        phone.length() >= 7
            ? phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4)
            : null);
    return result;
  }

  public Map<String, Object> list(Actor p, int page, int size, String keyword) {
    access.role(p, "COMMUNITY", "FAMILY");
    List<Object> args = new ArrayList<>();
    String scope = "(";
    if (p.hasRole("COMMUNITY")) {
      scope += "e.site_id IN(SELECT site_id FROM sys_user_site WHERE user_id=?) OR ";
      args.add(p.userId());
    }
    scope +=
        "e.id IN(SELECT elder_id FROM family_binding WHERE family_user_id=? AND status='APPROVED')"
            + " OR e.created_by=?)";
    args.add(p.userId());
    args.add(p.userId());
    args.add("%" + keyword + "%");
    var result =
        db.page(
            "SELECT e.id,e.site_id,e.name,e.birth_date,e.status,e.approval_status,s.name site_name"
                + " FROM elder e JOIN service_site s ON s.id=e.site_id WHERE "
                + scope
                + " AND e.name LIKE ? ORDER BY e.id DESC",
            args,
            page,
            size);
    return result;
  }

  public Map<String, Object> get(Actor p, long id) {
    var row = row(id);
    access.elder(p, row, false);
    return safe(row);
  }

  public Map<String, Object> save(Actor p, Long id, Map<String, Object> input) {
    access.permit(p, "elders:write", "COMMUNITY", "FAMILY");
    fields(input, "name", "siteId", "birthDate", "phone", "identityNumber", "status");
    long site = Values.id(input.get("siteId"));
    db.one("SELECT id FROM service_site WHERE id=?", site);
    String name = text(input, "name", 80, true), phone = text(input, "phone", 30, false);
    var birth = input.get("birthDate") == null ? null : date(input.get("birthDate"));
    if (birth != null && birth.isAfter(java.time.LocalDate.now()))
      throw ApiException.invalid("出生日期不能在未来");
    if (id == null) {
      boolean staff = p.hasRole("COMMUNITY") && access.assignedSite(p, site);
      if (!staff && !p.hasRole("FAMILY")) throw ApiException.forbidden();
      id =
          db.insert(
              "INSERT INTO"
                  + " elder(site_id,name,birth_date,phone,identity_cipher,created_by,approval_status)"
                  + " VALUES(?,?,?,?,?,?,?)",
              site,
              name,
              birth,
              phone,
              crypto.encrypt(text(input, "identityNumber", 30, false)),
              p.userId(),
              staff ? "APPROVED" : "PENDING");
      if (!staff)
        db.exec(
            "INSERT INTO family_binding(family_user_id,elder_id,status) VALUES(?,?,'PENDING')",
            p.userId(),
            id);
    } else {
      var elder = row(id);
      access.elder(p, elder, false);
      rule(num(elder.get("siteId")) == site, "首版不支持直接更换老人归属网点");
      db.exec(
          "UPDATE elder SET"
              + " name=?,birth_date=?,phone=COALESCE(?,phone),identity_cipher=COALESCE(?,identity_cipher)"
              + " WHERE id=?",
          name,
          birth,
          phone,
          crypto.encrypt(text(input, "identityNumber", 30, false)),
          id);
      if (input.containsKey("status")) {
        access.role(p, "COMMUNITY");
        if (!access.assignedSite(p, site)) throw ApiException.forbidden();
        db.exec(
            "UPDATE elder SET status=? WHERE id=?",
            choice(input.get("status"), "ACTIVE", "DISABLED"),
            id);
      }
    }
    if(input.containsKey("status")&&p.hasRole("COMMUNITY")&&access.assignedSite(p,site))db.exec("UPDATE elder SET status=? WHERE id=?",choice(input.get("status"),"ACTIVE","DISABLED"),id);
    db.audit(p, "ELDER_SAVE", "ELDER", id);
    return safe(row(id));
  }

  public Map<String, Object> health(Actor p, long id) {
    access.elder(p, row(id), true);
    var result = db.optional("SELECT * FROM elder_health WHERE elder_id=?", id);
    if (result == null)
      return map(
          "elderId",
          id,
          "diseaseCodes",
          List.of(),
          "allergenIds",
          List.of(),
          "avoidIngredientNames",
          List.of(),
          "preferredCategoryIds",
          List.of(),
          "dislikedCategoryIds",
          List.of(),
          "allergenDataComplete",
          false,
          "healthDataComplete",
          false);
    result.put("diseaseCodes", result.remove("diseases"));
    result.put("avoidIngredientNames", result.remove("avoidIngredients"));
    result.put("preferredCategoryIds", result.remove("preferredCategories"));
    result.put("dislikedCategoryIds", result.remove("dislikedCategories"));
    return result;
  }

  public Map<String, Object> saveHealth(Actor p, long id, Map<String, Object> input) {
    access.permit(p, "health:write", "COMMUNITY", "FAMILY");
    access.elder(p, db.one("SELECT * FROM elder WHERE id=? FOR UPDATE", id), true);
    fields(
        input,
        "diseaseCodes",
        "allergenIds",
        "avoidIngredientNames",
        "preferredCategoryIds",
        "dislikedCategoryIds",
        "healthNotes",
        "allergenDataComplete",
        "healthDataComplete");
    var allergens = ids(input.get("allergenIds"));
    for (long allergen : allergens)
      db.one("SELECT id FROM allergen WHERE id=? AND is_active=1", allergen);
    db.exec(
        "INSERT INTO"
            + " elder_health(elder_id,diseases_json,allergen_ids_json,avoid_ingredients_json,preferred_categories_json,disliked_categories_json,health_notes,allergen_data_complete,health_data_complete)"
            + " VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE"
            + " diseases_json=VALUES(diseases_json),allergen_ids_json=VALUES(allergen_ids_json),avoid_ingredients_json=VALUES(avoid_ingredients_json),preferred_categories_json=VALUES(preferred_categories_json),disliked_categories_json=VALUES(disliked_categories_json),health_notes=VALUES(health_notes),allergen_data_complete=VALUES(allergen_data_complete),health_data_complete=VALUES(health_data_complete)",
        id,
        db.json(strings(input.get("diseaseCodes"))),
        db.json(allergens),
        db.json(strings(input.get("avoidIngredientNames"))),
        db.json(ids(input.get("preferredCategoryIds"))),
        db.json(ids(input.get("dislikedCategoryIds"))),
        text(input, "healthNotes", 2000, false),
        yes(input.get("allergenDataComplete")),
        yes(input.get("healthDataComplete")));
    db.audit(p, "HEALTH_UPDATE", "ELDER", id);
    return health(p, id);
  }

  public Map<String, Object> requestBinding(Actor p, Map<String, Object> input) {
    access.role(p, "FAMILY");
    long elder = Values.id(input.get("elderId"));
    row(elder);
    Long proof = input.get("proofFileId") == null ? null : Values.id(input.get("proofFileId"));
    ownedFile(p, proof, "BINDING");
    var old =
        db.optional(
            "SELECT * FROM family_binding WHERE family_user_id=? AND elder_id=? FOR UPDATE",
            p.userId(),
            elder);
    if (old != null) {
      rule(!str(old.get("status")).equals("APPROVED"), "已经绑定此老人");
      db.exec(
          "UPDATE family_binding SET"
              + " status='PENDING',proof_file_id=?,reviewer_id=NULL,review_note=NULL,reviewed_at=NULL"
              + " WHERE id=?",
          proof,
          old.get("id"));
      db.audit(p,"BINDING_REQUEST","BINDING",old.get("id"));
      return db.one("SELECT id,status FROM family_binding WHERE id=?",old.get("id"));
    }
    long id =
        db.insert(
            "INSERT INTO family_binding(family_user_id,elder_id,proof_file_id) VALUES(?,?,?)",
            p.userId(),
            elder,
            proof);
    db.audit(p, "BINDING_REQUEST", "BINDING", id);
    return db.one("SELECT id,status FROM family_binding WHERE id=?", id);
  }

  public List<Map<String, Object>> bindings(Actor p, boolean pending) {
    if (!pending) {
      access.role(p, "FAMILY");
      return db.list(
          "SELECT b.id,b.elder_id,b.status,b.review_note,e.name elder_name,e.site_id FROM"
              + " family_binding b JOIN elder e ON e.id=b.elder_id WHERE b.family_user_id=? ORDER"
              + " BY b.id DESC",
          p.userId());
    }
    access.role(p, "COMMUNITY");
    return db.list(
        "SELECT b.*,e.name elder_name,e.site_id,u.real_name family_name FROM family_binding b JOIN"
            + " elder e ON e.id=b.elder_id JOIN sys_user u ON u.id=b.family_user_id WHERE e.site_id"
            + " IN(SELECT site_id FROM sys_user_site WHERE user_id=?) AND b.status='PENDING' ORDER"
            + " BY b.id",
        p.userId());
  }

  public Map<String, Object> reviewBinding(
      Actor p, long id, boolean approve, Map<String, Object> input) {
    access.permit(p, "bindings:review", "COMMUNITY");
    var b = db.one("SELECT * FROM family_binding WHERE id=? FOR UPDATE", id);
    var elder = row(num(b.get("elderId")));
    access.site(p, num(elder.get("siteId")));
    rule(num(b.get("familyUserId")) != p.userId(), "申请人不能审核自己的绑定");
    rule(str(b.get("status")).equals("PENDING"), "绑定已经处理");
    String note = text(input, "note", 500, true);
    db.exec(
        "UPDATE family_binding SET status=?,reviewer_id=?,review_note=?,reviewed_at=UTC_TIMESTAMP()"
            + " WHERE id=?",
        approve ? "APPROVED" : "REJECTED",
        p.userId(),
        note,
        id);
    if (approve) db.exec("UPDATE elder SET approval_status='APPROVED' WHERE id=?", elder.get("id"));
    db.audit(p, "BINDING_REVIEW", "BINDING", id);
    return db.one("SELECT * FROM family_binding WHERE id=?", id);
  }

  public Map<String, Object> applySubsidy(Actor p, Map<String, Object> input) {
    access.role(p, "FAMILY", "COMMUNITY");
    long elder = Values.id(input.get("elderId"));
    access.elder(p, row(elder), false);
    var files = ids(input.get("proofFileIds"));
    for (long file : files) ownedFile(p, file, "SUBSIDY");
    long id =
        db.insert(
            "INSERT INTO subsidy_application(elder_id,applicant_user_id,proof_file_ids_json)"
                + " VALUES(?,?,?)",
            elder,
            p.userId(),
            db.json(files));
    db.audit(p, "SUBSIDY_REQUEST", "ELDER", elder);
    return db.one("SELECT * FROM subsidy_application WHERE id=?", id);
  }

  public Map<String, Object> subsidy(Actor p, long id) {
    access.role(p, "FAMILY", "COMMUNITY");
    var app =
        db.one(
            "SELECT a.*,e.name elder_name,e.site_id FROM subsidy_application a JOIN elder e ON"
                + " e.id=a.elder_id WHERE a.id=?",
            id);
    boolean staff = p.hasRole("COMMUNITY") && access.assignedSite(p, num(app.get("siteId")));
    boolean own = p.hasRole("FAMILY") && num(app.get("applicantUserId")) == p.userId();
    if (!staff && !own) throw ApiException.forbidden();
    return app;
  }

  public Map<String, Object> subsidies(Actor p, int page, int size, String keyword) {
    return subsidies(p,page,size,keyword,"");
  }
  public Map<String,Object> subsidies(Actor p,int page,int size,String keyword,String status) {return subsidies(p,page,size,keyword,status,null);}
  public Map<String,Object> subsidies(Actor p,int page,int size,String keyword,String status,Long siteId) {
    access.role(p, "FAMILY", "COMMUNITY");
    List<Object> args = new ArrayList<>();
    List<String> scopes = new ArrayList<>();
    if (p.hasRole("COMMUNITY")) {
      scopes.add("e.site_id IN(SELECT site_id FROM sys_user_site WHERE user_id=?)");
      args.add(p.userId());
    }
    if (p.hasRole("FAMILY")) {
      scopes.add("a.applicant_user_id=?");
      args.add(p.userId());
    }
    String scope = "(" + String.join(" OR ", scopes) + ")";
    args.add("%"+keyword.trim()+"%");args.add("%"+keyword.trim()+"%");
    args.add(status);args.add(status);args.add(siteId);args.add(siteId);
    return db.page(
        "SELECT a.*,e.name elder_name,e.site_id FROM subsidy_application a JOIN elder e ON"
            + " e.id=a.elder_id WHERE "
            + scope
            + " AND (e.name LIKE ? OR COALESCE(a.review_note,'') LIKE ?) AND (?='' OR a.status=?) AND (? IS NULL OR e.site_id=?) ORDER BY a.id DESC",
        args,
        page,
        size);
  }

  public Map<String, Object> reviewSubsidy(
      Actor p, long id, boolean approve, Map<String, Object> input) {
    access.permit(p, "subsidies:review", "COMMUNITY");
    db.one("SELECT id FROM subsidy_application WHERE id=? FOR UPDATE", id);
    var app = subsidy(p, id);
    if (!access.assignedSite(p, num(app.get("siteId")))) throw ApiException.forbidden();
    rule(num(app.get("applicantUserId")) != p.userId(), "申请人不能审核自己的补贴申请");
    rule(str(app.get("status")).equals("PENDING"), "申请已经处理");
    String note = text(input, "note", 500, true);
    db.exec(
        "UPDATE subsidy_application SET"
            + " status=?,reviewer_id=?,review_note=?,reviewed_at=UTC_TIMESTAMP() WHERE id=?",
        approve ? "APPROVED" : "REJECTED",
        p.userId(),
        note,
        id);
    java.time.LocalDate from=input.get("validFrom")==null?java.time.LocalDate.now(BusinessTime.ZONE):date(input.get("validFrom")),to=input.get("validTo")==null?null:date(input.get("validTo"));
    if(to!=null)rule(!to.isBefore(from),"补贴结束日期不能早于开始日期");
    db.one("SELECT id FROM elder WHERE id=? FOR UPDATE",app.get("elderId"));
    if(approve)db.exec("INSERT INTO subsidy_entitlement(elder_id,is_active,source_application_id,activated_at,valid_from,valid_to) VALUES(?,1,?,UTC_TIMESTAMP(),?,?) ON DUPLICATE KEY UPDATE is_active=1,source_application_id=VALUES(source_application_id),activated_at=UTC_TIMESTAMP(),valid_from=VALUES(valid_from),valid_to=VALUES(valid_to)",app.get("elderId"),id,from,to);
    db.exec("INSERT INTO subsidy_history(elder_id,application_id,action,note,valid_from,valid_to,operator_user_id) VALUES(?,?,?,?,?,?,?)",app.get("elderId"),id,approve?"APPROVED":"REJECTED",note,approve?from:null,approve?to:null,p.userId());
    db.audit(p, "SUBSIDY_REVIEW", "SUBSIDY", id);
    return subsidy(p, id);
  }

  public Map<String,Object> revokeBinding(Actor p,long id,Map<String,Object> input){
    access.role(p,"FAMILY","COMMUNITY");var b=db.one("SELECT * FROM family_binding WHERE id=? FOR UPDATE",id);
    boolean owner=p.hasRole("FAMILY")&&num(b.get("familyUserId"))==p.userId();boolean staff=p.hasRole("COMMUNITY")&&access.assignedSite(p,num(row(num(b.get("elderId"))).get("siteId")));
    if(!owner&&!staff)throw ApiException.forbidden();String note=text(input,"note",500,true);
    if(str(b.get("status")).equals("REVOKED"))return b;
    db.exec("UPDATE family_binding SET status='REVOKED',reviewer_id=?,review_note=?,reviewed_at=UTC_TIMESTAMP() WHERE id=?",p.userId(),note,id);
    db.audit(p,"BINDING_REVOKE","BINDING",id);return db.one("SELECT * FROM family_binding WHERE id=?",id);
  }
  public Map<String,Object> managedBindings(Actor p,int page,int size,String status,String keyword,Long siteId){access.role(p,"COMMUNITY");return db.page("SELECT b.*,e.name elder_name,u.real_name family_name FROM family_binding b JOIN elder e ON e.id=b.elder_id JOIN sys_user u ON u.id=b.family_user_id WHERE e.site_id IN(SELECT site_id FROM sys_user_site WHERE user_id=?) AND (?='' OR b.status=?) AND (e.name LIKE ? OR u.real_name LIKE ?) AND (? IS NULL OR e.site_id=?) ORDER BY b.id DESC",Arrays.asList(p.userId(),status,status,"%"+keyword+"%","%"+keyword+"%",siteId,siteId),page,size);}
  public Map<String,Object> bindingHistory(Actor p,int page,int size){
    access.role(p,"FAMILY","COMMUNITY");List<Object> args=new ArrayList<>();String scope="";
    if(p.hasRole("COMMUNITY")){scope="e.site_id IN(SELECT site_id FROM sys_user_site WHERE user_id=?) OR ";args.add(p.userId());}
    scope+="b.family_user_id=?";args.add(p.userId());
    return db.page("SELECT h.*,e.name elder_name,b.family_user_id FROM binding_history h JOIN family_binding b ON b.id=h.binding_id JOIN elder e ON e.id=b.elder_id WHERE ("+scope+") ORDER BY h.id DESC",args,page,size);
  }
  public Map<String,Object> terminateSubsidy(Actor p,long elder,Map<String,Object> input){
    access.permit(p,"subsidies:review","COMMUNITY");var row=rowForUpdate(elder);access.site(p,num(row.get("siteId")));String note=text(input,"note",500,true);
    var entitlement=db.one("SELECT * FROM subsidy_entitlement WHERE elder_id=? FOR UPDATE",elder);
    if(!yes(entitlement.get("isActive")))return entitlement;
    db.exec("UPDATE subsidy_entitlement SET is_active=0 WHERE elder_id=?",elder);
    db.exec("INSERT INTO subsidy_history(elder_id,application_id,action,note,valid_from,valid_to,operator_user_id) VALUES(?,?,'TERMINATED',?,?,?,?)",elder,entitlement.get("sourceApplicationId"),note,entitlement.get("validFrom"),entitlement.get("validTo"),p.userId());
    db.audit(p,"SUBSIDY_TERMINATE","ELDER",elder);return db.one("SELECT * FROM subsidy_entitlement WHERE elder_id=?",elder);
  }
  public Map<String,Object> subsidyHistory(Actor p,long elder){access.elder(p,row(elder),false);return map("entitlement",db.optional("SELECT * FROM subsidy_entitlement WHERE elder_id=?",elder),"records",db.list("SELECT * FROM subsidy_history WHERE elder_id=? ORDER BY id DESC",elder),"total",db.count("SELECT COUNT(*) FROM subsidy_history WHERE elder_id=?",elder));}

  private void ownedFile(Actor p, Long id, String purpose) {
    if (id != null
        && db.count(
                "SELECT COUNT(*) FROM stored_file WHERE id=? AND owner_user_id=? AND purpose=?",
                id,
                p.userId(),
                purpose)
            == 0) throw ApiException.forbidden();
  }
}
