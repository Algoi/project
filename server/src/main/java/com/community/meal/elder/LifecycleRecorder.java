package com.community.meal.elder;
import com.community.meal.common.*;import com.community.meal.contracts.BusinessEvent;import java.util.*;import org.springframework.stereotype.Component;import org.springframework.context.event.EventListener;
@Component public class LifecycleRecorder {
 private final Db db;public LifecycleRecorder(Db db){this.db=db;}
 @EventListener public void on(BusinessEvent e){if(!Set.of("BINDING_REQUEST","BINDING_REVIEW","BINDING_REVOKE").contains(e.action()))return;var b=db.one("SELECT * FROM family_binding WHERE id=?",e.resourceId());db.exec("INSERT INTO binding_history(binding_id,action,status,note,operator_user_id) VALUES(?,?,?,?,?)",b.get("id"),e.action().equals("BINDING_REQUEST")?"REQUESTED":e.action().equals("BINDING_REVOKE")?"REVOKED":"REVIEWED",b.get("status"),b.get("reviewNote"),e.actorId());}
}