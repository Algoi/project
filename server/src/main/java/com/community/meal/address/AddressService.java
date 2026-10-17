package com.community.meal.address;
import com.community.meal.auth.Access;import com.community.meal.common.*;import com.community.meal.contracts.Actor;
import static com.community.meal.common.Values.*;import java.util.*;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service @Transactional public class AddressService {
 private final Db db;private final Access access;public AddressService(Db db,Access access){this.db=db;this.access=access;}
 public Map<String,Object> list(Actor p,Long elder,int page,int size,String keyword){access.role(p,"FAMILY");return db.page("SELECT a.id,a.elder_id,e.name elder_name,a.label,a.contact_name,a.contact_phone,a.address,a.is_default FROM delivery_address a JOIN elder e ON e.id=a.elder_id WHERE a.owner_user_id=? AND EXISTS(SELECT 1 FROM family_binding b WHERE b.family_user_id=? AND b.elder_id=a.elder_id AND b.status='APPROVED') AND (? IS NULL OR a.elder_id=?) AND (a.label LIKE ? OR a.address LIKE ?) ORDER BY a.is_default DESC,a.id DESC",Arrays.asList(p.userId(),p.userId(),elder,elder,"%"+keyword+"%","%"+keyword+"%"),page,size);}
 public Map<String,Object> save(Actor p,Long id,Map<String,Object> in){access.role(p,"FAMILY");fields(in,"elderId","label","contactName","contactPhone","address","isDefault");long elder=Values.id(in.get("elderId"));if(!access.linkedFamily(p,elder))throw ApiException.forbidden();db.one("SELECT id FROM elder WHERE id=? FOR UPDATE",elder);
  if(id!=null){var old=owned(p,id);rule(num(old.get("elderId"))==elder,"常用地址不能更换关联老人");}
  String label=text(in,"label",40,true),name=text(in,"contactName",80,true),phone=text(in,"contactPhone",30,true),address=text(in,"address",255,true);boolean primary=yes(in.get("isDefault"));
  if(primary)db.exec("UPDATE delivery_address SET is_default=0 WHERE owner_user_id=? AND elder_id=?",p.userId(),elder);
  if(id==null)id=db.insert("INSERT INTO delivery_address(owner_user_id,elder_id,label,contact_name,contact_phone,address,is_default) VALUES(?,?,?,?,?,?,?)",p.userId(),elder,label,name,phone,address,primary);
  else db.exec("UPDATE delivery_address SET label=?,contact_name=?,contact_phone=?,address=?,is_default=? WHERE id=?",label,name,phone,address,primary,id);
  db.audit(p,"ADDRESS_SAVE","ADDRESS",id);return db.one("SELECT id,elder_id,label,contact_name,contact_phone,address,is_default FROM delivery_address WHERE id=?",id);
 }
 private Map<String,Object> owned(Actor p,long id){var a=db.one("SELECT * FROM delivery_address WHERE id=?",id);if(num(a.get("ownerUserId"))!=p.userId()||!access.linkedFamily(p,num(a.get("elderId"))))throw ApiException.forbidden();return a;}
 public Map<String,Object> delete(Actor p,long id){access.role(p,"FAMILY");owned(p,id);db.exec("DELETE FROM delivery_address WHERE id=?",id);db.audit(p,"ADDRESS_DELETE","ADDRESS",id);return map("id",id);}
}
