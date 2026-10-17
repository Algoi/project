package com.community.meal.contracts;

public interface FileReadAccess {
  boolean mayRead(Actor actor, long fileId, String purpose);
}
