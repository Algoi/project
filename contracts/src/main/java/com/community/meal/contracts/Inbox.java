package com.community.meal.contracts;
public interface Inbox { void send(long userId,String sourceKey,String title,String body,String path); }
