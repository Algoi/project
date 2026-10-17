package com.community.meal.messages;
import com.community.meal.common.*;import com.community.meal.contracts.*;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service @Transactional
public class MessageService implements Inbox {
 private final Db db;public MessageService(Db db){this.db=db;}
 public void send(long user,String source,String title,String body,String path){db.exec("INSERT IGNORE INTO inbox_message(user_id,source_key,title,body,target_path) VALUES(?,?,?,?,?)",user,source,title,body,path);}
 public Map<String,Object> list(Actor p,int page,int size,boolean unread,String keyword){return db.page("SELECT id,title,body,target_path,read_at,created_at FROM inbox_message WHERE user_id=? AND (?=0 OR read_at IS NULL) AND (title LIKE ? OR body LIKE ?) ORDER BY id DESC",List.of(p.userId(),unread?1:0,"%"+keyword+"%","%"+keyword+"%"),page,size);}
 public Map<String,Object> count(Actor p){return Values.map("count",db.count("SELECT COUNT(*) FROM inbox_message WHERE user_id=? AND read_at IS NULL",p.userId()));}
 public Map<String,Object> read(Actor p,long id){var row=db.one("SELECT user_id FROM inbox_message WHERE id=?",id);if(Values.num(row.get("userId"))!=p.userId())throw ApiException.forbidden();db.exec("UPDATE inbox_message SET read_at=COALESCE(read_at,UTC_TIMESTAMP()) WHERE id=? AND user_id=?",id,p.userId());return count(p);}
 public Map<String,Object> readAll(Actor p){db.exec("UPDATE inbox_message SET read_at=UTC_TIMESTAMP() WHERE user_id=? AND read_at IS NULL",p.userId());return count(p);}
}
